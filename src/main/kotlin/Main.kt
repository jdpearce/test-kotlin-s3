import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityRequest
import com.amazonaws.services.securitytoken.model.AssumeRoleWithWebIdentityResult
import com.amazonaws.services.securitytoken.model.Credentials
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.util.*

private val log = getLogger("AwsTest")

const val REGION = "us-east-1"
val BUCKET = "nx-cache-dev"
//val BUCKET = "bucket-${UUID.randomUUID()}"

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
    var s3: AmazonS3 = getClient(env)

    if (env.containsKey("AWS_S3_ENDPOINT")) {
        s3.setEndpoint(env["AWS_S3_ENDPOINT"])
    }
    if (env.containsKey("AWS_S3_ACCELERATED") && env["AWS_S3_ACCELERATED"] == "TRUE") {
        s3.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build())
    }

    log.info("Starting timer with delay....")

    val timer = Timer()
    timer.schedule(object : TimerTask() {
        override fun run() {
            log.info("Checking whether we are still authenticated...")
            if (!isStillAuthenticated(s3)) {
                log.info("Getting a new client.")
                s3.shutdown()
                s3 = getClient(env)
            }

            log.info("Executing with delay...")
            val key = UUID.randomUUID().toString()
            s3.putObject(BUCKET, key, "Testing with Kotlin SDK")
            log.info("Object $BUCKET/$key created successfully!")
            cleanUp(s3, key)
        }
    }, 0, 60000)


}

fun isStillAuthenticated(s3: AmazonS3): Boolean {
    return try {
        s3.listObjects(ListObjectsRequest(BUCKET, null, null, null,0))
        true
    } catch (ex: Exception) {
        log.info("No longer authenticated with the current credentials.")
        false
    }
}

fun getClient(env: Map<String, String>): AmazonS3 {
    return if (env.containsKey("AWS_ROLE_ARN")) {
        log.info("Role ARN provided, attempting to create client with temp credentials.")

        val stsClient = AWSSecurityTokenServiceClientBuilder.standard()
            .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
            .withRegion(Regions.US_EAST_1)
            .build()

        val token = File(env["AWS_WEB_IDENTITY_TOKEN_FILE"]).readText()
        val roleRequest = AssumeRoleWithWebIdentityRequest()
            .withRoleArn(env["AWS_ROLE_ARN"])
            .withWebIdentityToken(token)
            .withRoleSessionName("nxApiRoleSession")

        val roleResponse: AssumeRoleWithWebIdentityResult = stsClient.assumeRoleWithWebIdentity(roleRequest)
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
            .withRegion(Regions.US_EAST_1)
            .build()
    } else if (env.containsKey("AWS_ACCESS_KEY_ID") && env.containsKey("AWS_SECRET_ACCESS_KEY")) {
        log.info("AWS_ACCESS_KEY_ID provided, creating client the old-fashioned way.")
        AmazonS3ClientBuilder.standard()
            .withCredentials(
                AWSStaticCredentialsProvider(
                    BasicAWSCredentials(env["AWS_ACCESS_KEY_ID"], env["AWS_SECRET_ACCESS_KEY"])
                )
            )
            .withRegion(Regions.US_EAST_1)
            .build()
    } else {
        val message = "No S3 credentials provided"
        log.error(message)
        throw Exception(message)
    }
}

fun cleanUp(s3: AmazonS3, key: String) {
    log.info("Deleting object $BUCKET/$key...")
    s3.deleteObject(BUCKET, key)
    log.info("Object $BUCKET/$key deleted successfully!")
}