package io.x1ao.login

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider

class Account(private val username: String) : AbstractUser() {
    var nickname = username

    override fun setAuthProvider(authProvider: AuthProvider) {

    }

    override fun principal(): JsonObject {
        return JsonObject().put("username", username).put("nickname", nickname)
    }

    override fun doIsPermitted(permission: String, resultHandler: Handler<AsyncResult<Boolean>>) {

    }
}
