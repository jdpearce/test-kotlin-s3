import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
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

const val BUCKET = "nx-cache-dev"
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
    val fileServer = S3BasedFileServer(env)

    log.info("Starting timer with delay....")

    val timer = Timer()
    timer.schedule(object : TimerTask() {
        override fun run() {
            log.info("Executing with delay...")
            val key = UUID.randomUUID().toString()
            fileServer.putObject(key)
            fileServer.cleanUp(key)
        }
    }, 0, 60000)


}

class S3BasedFileServer(private val env: Map<String, String>)  {

    companion object {
        const val AWS_ROLE_ARN = "AWS_ROLE_ARN"
        const val AWS_WEB_IDENTITY_TOKEN_FILE = "AWS_WEB_IDENTITY_TOKEN_FILE"
        const val AWS_S3_ACCESS_KEY_ID = "AWS_S3_ACCESS_KEY_ID"
        const val AWS_S3_SECRET_ACCESS_KEY = "AWS_S3_SECRET_ACCESS_KEY"
        const val AWS_S3_BUCKET = "AWS_S3_BUCKET"
        const val AWS_S3_ENDPOINT = "AWS_S3_ENDPOINT"
        const val AWS_S3_ACCELERATED = "AWS_S3_ACCELERATED"
        const val NX_API_ROLE_SESSION = "nxApiRoleSession" // This is an arbitrary value
    }


    private var _client: AmazonS3? = null
    private val client: AmazonS3
        get() {
            when {
                _client == null -> {
                    _client = getNewClient()
                }

                env.containsKey(AWS_ROLE_ARN) && !isStillAuthenticated(_client) -> {
                    _client?.shutdown()
                    _client = getNewClient()
                }
            }

            return _client ?: throw AssertionError("Amazon S3 Client unexpectedly null.")
        }

    /**
     * Unfortunately this is the best way I could find to check whether the credentials we have are
     * still useful. We _could_ implement some kind of timer based on the expected timeout for
     * the credentials we're given, but this is much simpler and possibly less error-prone
     */
    private fun isStillAuthenticated(s3: AmazonS3?): Boolean {
        log.info("Checking whether we are still authenticated...")
        if (s3 == null) {
            log.error("Client is unexpectedly null, returning false.")
            return false
        }

        return try {
            s3.listObjects(ListObjectsRequest(BUCKET, null, null, null, 0))
            true
        } catch (ex: Exception) {
            log.info("No longer authenticated with the current credentials.")
            false
        }
    }

    private fun getNewClient(): AmazonS3 {
        log.info("Getting a new AmazonS3 client.")

        val s3: AmazonS3 = if (env.containsKey(AWS_ROLE_ARN)) {
            log.info("$AWS_ROLE_ARN provided, attempting to create client with temp credentials.")

            val stsClient = AWSSecurityTokenServiceClientBuilder.standard()
                .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
                .build()

            val token = env[AWS_WEB_IDENTITY_TOKEN_FILE]?.let { File(it).readText() }
            val roleRequest = AssumeRoleWithWebIdentityRequest()
                .withRoleArn(env[AWS_ROLE_ARN])
                .withWebIdentityToken(token)
                .withRoleSessionName(NX_API_ROLE_SESSION)

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
                .build()
        } else if (env.containsKey(AWS_S3_ACCESS_KEY_ID) && env.containsKey(AWS_S3_SECRET_ACCESS_KEY)) {
            log.info("$AWS_S3_ACCESS_KEY_ID provided, creating client the old-fashioned way.")
            AmazonS3ClientBuilder.standard()
                .withCredentials(
                    AWSStaticCredentialsProvider(
                        BasicAWSCredentials(env[AWS_S3_ACCESS_KEY_ID], env[AWS_S3_SECRET_ACCESS_KEY])
                    )
                )
                .build()
        } else {
            throw Exception("No S3 credentials provided")
        }

        if (env.containsKey(AWS_S3_ENDPOINT)) {
            s3.setEndpoint(env[AWS_S3_ENDPOINT])
        }

        if (env.containsKey(AWS_S3_ACCELERATED) && env[AWS_S3_ACCELERATED] == "TRUE") {
            s3.setS3ClientOptions(S3ClientOptions.builder().setAccelerateModeEnabled(true).build())
        }

        return s3
    }

    fun putObject(key: String) {
        log.info("Creating object $BUCKET/$key...")
        client.putObject(BUCKET, key, "Testing with Kotlin SDK")
        log.info("Object $BUCKET/$key created successfully!")
    }


    fun cleanUp(key: String) {
        log.info("Deleting object $BUCKET/$key...")
        client.deleteObject(BUCKET, key)
        log.info("Object $BUCKET/$key deleted successfully!")
    }
}