import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.services.securitytoken.model.AssumeRoleResult
import com.amazonaws.services.securitytoken.model.Credentials
import org.slf4j.LoggerFactory.getLogger
import java.util.*

private val log = getLogger("AwsTest")

const val REGION = "us-west-2"
val BUCKET = "bucket-${UUID.randomUUID()}"
const val KEY = "key"

/**
 * When starting up the pod, if configured correctly for IAM Role access
 * It should be provided:
 *
 * AWS_DEFAULT_REGION
 * AWS_REGION
 * AWS_ROLE_ARN
 * AWS_WEB_IDENTITY_TOKEN_FILE
 * AWS_STS_REGIONAL_ENDPOINTS
 *
 * If we're using IAM User access we will be provided with:
 *
 * AWS_ACCESS_KEY_ID
 * AWS_SECRET_ACCESS_KEY
 *
 * So we can switch on whether we have AWS_ACCESS_KEY_ID or AWS_ROLE_ARN
 */
fun main() {
    val env = System.getenv()

    val s3: AmazonS3 = if (env.containsKey("AWS_ROLE_ARN")) {
        log.info("Role ARN provided, attempting to create client with temp credentials.")

        val stsClient = AWSSecurityTokenServiceClientBuilder.standard()
            .withCredentials(ProfileCredentialsProvider())
            .withRegion(Regions.DEFAULT_REGION)
            .build()

        val roleRequest = AssumeRoleRequest()
            .withRoleArn(env["AWS_ROLE_ARN"])
            .withRoleSessionName("nxApiRoleSession")

        val roleResponse: AssumeRoleResult = stsClient.assumeRole(roleRequest)
        val sessionCredentials: Credentials = roleResponse.credentials

        AmazonS3ClientBuilder.standard()
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicSessionCredentials(
                        sessionCredentials.accessKeyId,
                        sessionCredentials.secretAccessKey,
                        sessionCredentials.sessionToken
                    )
                )
            )
            .withRegion(Regions.DEFAULT_REGION)
            .build()
    } else if (env.containsKey("AWS_ACCESS_KEY_ID") && env.containsKey("AWS_SECRET_ACCESS_KEY")) {
        log.info("AWS_ACCESS_KEY_ID provided, creating client the old-fashioned way.")
        AmazonS3ClientBuilder.standard()
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicAWSCredentials(env["AWS_ACCESS_KEY_ID"], env["AWS_SECRET_ACCESS_KEY"])
                )
            )
            .withRegion(Regions.DEFAULT_REGION)
            .build()
    } else {
        val message = "No S3 credentials provided"
        log.error(message)
        throw Exception(message)
    }

    if (env.containsKey("AWS_S3_ENDPOINT")) {
        s3.setEndpoint(env["AWS_S3_ENDPOINT"])
    }
    if (env.containsKey("AWS_S3_ACCELERATED") && env["AWS_S3_ACCELERATED"] == "TRUE") {
        s3.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build())
    }

    setupTutorial(s3)

    s3.putObject(BUCKET, KEY, "Testing with Kotlin SDK")
    log.info("Object $BUCKET/$KEY created successfully!")

    cleanUp(s3)
}


fun setupTutorial(s3: AmazonS3) {
    log.info("Creating bucket $BUCKET...")
    s3.createBucket(BUCKET, REGION)
    log.info("Bucket $BUCKET created successfully!")
}

fun cleanUp(s3: AmazonS3) {
    log.info("Deleting object $BUCKET/$KEY...")
    s3.deleteObject(BUCKET, KEY)
    log.info("Object $BUCKET/$KEY deleted successfully!")

    log.info("Deleting bucket $BUCKET...")
    s3.deleteBucket(BUCKET)
    log.info("Bucket $BUCKET deleted successfully!")
}