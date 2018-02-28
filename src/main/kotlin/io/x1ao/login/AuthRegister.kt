package io.x1ao.login

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User
import io.vertx.ext.mongo.MongoClient

class AuthRegister(private val client: MongoClient) : AuthProvider {
    private val field = JsonObject()

    override fun authenticate(authInfo: JsonObject, resultHandler: Handler<AsyncResult<User>>) {
        val username = authInfo.getString("username")?.toLowerCase() ?: ""
        val password = authInfo.getString("password") ?: ""
        if (username.isNotEmpty() && password.isNotEmpty()) {
            client.findOne("accounts", JsonObject().put("username", username), field) { res ->
                if (res.succeeded() && res.result()?.getString("password") == password) {
                    val account = Account(username)
                    account.nickname = res.result().getString("nickname")
                    resultHandler.handle(Future.succeededFuture(account))
                } else resultHandler.handle(Future.failedFuture("username or password is not correct!"))
            }
        } else resultHandler.handle(Future.failedFuture("!!"))
    }

    fun register(userInfo: JsonObject): Future<Boolean> {
        val username = userInfo.getString("username")?.toLowerCase() ?: ""
        val password = userInfo.getString("password") ?: ""
        val nickname = userInfo.getString("nickname") ?: ""
        return if (username.length in 4..12 && password.length in 8..18) {
            Future.future<Boolean> { fut ->
                val account = JsonObject().put("username", username)
                client.findOne("accounts", account, field) { res ->
                    if (res.succeeded() && res.result() == null) {
                        client.save("accounts", account.put("password", password)
                            .put("nickname", if (nickname.isEmpty()) username else nickname)) { res1 ->
                            fut.complete(res1.succeeded())
                        }
                    } else fut.fail("username has been registered")
                }
            }
        } else Future.failedFuture("not match the rule")
    }
}
