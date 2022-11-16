import aws.sdk.kotlin.runtime.auth.credentials.EnvironmentCredentialsProvider
import aws.sdk.kotlin.services.s3.*
import aws.sdk.kotlin.services.s3.model.BucketLocationConstraint
import aws.smithy.kotlin.runtime.content.ByteStream
import org.slf4j.LoggerFactory.getLogger
import kotlinx.coroutines.runBlocking
import java.util.*

private val log = getLogger("AwsTest")

const val REGION = "us-west-2"
val BUCKET = "bucket-${UUID.randomUUID()}"
const val KEY = "key"

fun main() {
    log.info("AWS_ACCESS_KEY_ID = ${System.getenv("AWS_ACCESS_KEY_ID")}")
//    log.info("AWS_SECRET_ACCESS_KEY = ${System.getenv("AWS_SECRET_ACCESS_KEY")}")

    runBlocking {
        S3Client
            .fromEnvironment {
                region = REGION
                credentialsProvider = EnvironmentCredentialsProvider()
            }
            .use { s3 ->
                setupTutorial(s3)

                log.info("Creating object $BUCKET/$KEY...")

                s3.putObject {
                    bucket = BUCKET
                    key = KEY
                    body = ByteStream.fromString("Testing with the Kotlin SDK")
                }

                log.info("Object $BUCKET/$KEY created successfully!")

                cleanUp(s3)
            }
    }
}


suspend fun setupTutorial(s3: S3Client) {
    log.info("Creating bucket $BUCKET...")
    s3.createBucket {
        bucket = BUCKET
        createBucketConfiguration {
            locationConstraint = BucketLocationConstraint.fromValue(REGION)
        }
    }
    log.info("Bucket $BUCKET created successfully!")
}

suspend fun cleanUp(s3: S3Client) {
    log.info("Deleting object $BUCKET/$KEY...")
    s3.deleteObject {
        bucket = BUCKET
        key = KEY
    }
    log.info("Object $BUCKET/$KEY deleted successfully!")

    log.info("Deleting bucket $BUCKET...")
    s3.deleteBucket {
        bucket = BUCKET
    }
    log.info("Bucket $BUCKET deleted successfully!")
}