import com.amazonaws.HttpMethod
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import org.slf4j.LoggerFactory.getLogger
import kotlinx.coroutines.runBlocking
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
 * So we can switch on
 */
fun main() {
    val s3AccessKey = System.getenv("AWS_ACCESS_KEY_ID")
    val s3SecretKey = System.getenv("AWS_SECRET_ACCESS_KEY")
    val s3Endpoint = System.getenv("AWS_S3_ENDPOINT")
    val s3Accelerated = System.getenv("AWS_S3_ACCELERATED") == "TRUE"

    log.info("AWS_ACCESS_KEY_ID = $s3AccessKey")

    val s3 = AmazonS3Client(BasicAWSCredentials(s3AccessKey, s3SecretKey))
    if (s3Endpoint != null) {
        s3.setEndpoint(s3Endpoint)
    }
    if (s3Accelerated) {
        s3.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build())
    }

    setupTutorial(s3)

    s3.putObject(BUCKET, KEY, "Testing with Kotlin SDK")
    log.info("Object $BUCKET/$KEY created successfully!")

    cleanUp(s3)
}


fun setupTutorial(s3: AmazonS3Client) {
    log.info("Creating bucket $BUCKET...")
    s3.createBucket(BUCKET, REGION)
    log.info("Bucket $BUCKET created successfully!")
}

fun cleanUp(s3: AmazonS3Client) {
    log.info("Deleting object $BUCKET/$KEY...")
    s3.deleteObject (BUCKET, KEY)
    log.info("Object $BUCKET/$KEY deleted successfully!")

    log.info("Deleting bucket $BUCKET...")
    s3.deleteBucket(BUCKET)
    log.info("Bucket $BUCKET deleted successfully!")
}