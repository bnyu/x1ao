package io.x1ao

import io.vertx.core.Vertx

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        Vertx.vertx().deployVerticle(MainVerticle::class.java.name)
    }
}
