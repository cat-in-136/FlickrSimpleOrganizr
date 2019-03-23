package io.github.cat_in_136.flickrsimpleorganizr

import com.github.scribejava.apis.FlickrApi
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.*
import com.github.scribejava.core.oauth.OAuth10aService
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class FlickrClient(apiKey: String, sharedSecret: String, parentJob: Job?) : CoroutineScope {
    private var job: Job = Job(parentJob)

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + this.job

    private val oauthService: OAuth10aService

    var accessToken : OAuth1AccessToken? = null

    init {
        this.oauthService = ServiceBuilder(apiKey)
                .apiSecret(sharedSecret)
                .build(FlickrApi.instance(FlickrApi.FlickrPerm.WRITE))
    }

    fun userAuthStep1To2(): Deferred<Pair<OAuth1RequestToken, String>> = async {
        accessToken = null
        val requestToken = oauthService.requestToken
        val authURL = oauthService.getAuthorizationUrl(requestToken)

        return@async Pair(requestToken, authURL)
    }

    fun userAuthStep3(requestToken: OAuth1RequestToken, verifierCode: String) = async {
        accessToken = oauthService.getAccessToken(requestToken, verifierCode)
        return@async accessToken
    }

    suspend fun access(request: OAuthRequest) = async(Dispatchers.Default) {
        oauthService.signRequest(accessToken, request)
        return@async suspendCoroutine<Triple<Number, Map<String,String>, String>> { continuation ->
            oauthService.execute(request, object : OAuthAsyncRequestCallback<Response> {
                override fun onCompleted(response: Response?) {
                    if (response is Response) {
                        continuation.resume(Triple(response.code, response.headers, response.body))
                    } else {
                        continuation.resumeWithException(Exception("Unknown Error"))
                    }
                }

                override fun onThrowable(throwable: Throwable?) {
                    if (throwable is Throwable) {
                        continuation.resumeWithException(throwable)
                    } else {
                        continuation.resumeWithException(Exception("Unknown Error"))
                    }
                }
            }, null)
        }
    }
}
