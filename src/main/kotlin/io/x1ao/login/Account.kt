package io.x1ao.login

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider

class Account(private val username: String) : AbstractUser() {

    override fun setAuthProvider(authProvider: AuthProvider) {

    }

    override fun principal(): JsonObject {
        return JsonObject().put("author", username)
    }

    override fun doIsPermitted(permission: String, resultHandler: Handler<AsyncResult<Boolean>>) {

    }
}
