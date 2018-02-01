package io.x1ao.yao

import io.vertx.core.AbstractVerticle
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.templ.MVELTemplateEngine
import java.time.LocalTime

class MainVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val engine = MVELTemplateEngine.create().setExtension(".html")

        fun templateHandler(templateFileName: String) = { ctx: RoutingContext ->
            engine.render(ctx, "templates/", templateFileName) { res ->
                if (res.succeeded())
                    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
                else
                    ctx.fail(res.cause())
            }
        }

        router.route("/midnight").handler { ctx ->
            val time = LocalTime.now()
            var seconds = time.second
            val result = if (time.hour == 0 && time.minute == 0) false else {
                if (time.hour != 23 || time.minute != 59 || seconds < 40) {
                    seconds = 40
                    Runtime.getRuntime().exec("cmd /c time 23:59:$seconds")
                    println("time changed")
                }
                true
            }
            ctx.put("result", result)
            ctx.put("seconds", 60 - seconds)
            templateHandler("midnight").invoke(ctx)
        }

        router.route("/favicon.ico").handler(FaviconHandler.create("resource/favicon.ico"))
        router.route("/").handler(templateHandler("index"))
        router.route("/*").handler(templateHandler("404"))
        vertx.createHttpServer().requestHandler(router::accept).listen(8080)
    }
}

