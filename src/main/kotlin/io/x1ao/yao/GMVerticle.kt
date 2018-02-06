package io.x1ao.yao

import io.vertx.core.AbstractVerticle
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.templ.MVELTemplateEngine
import java.io.File
import java.time.LocalTime

class GMVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val engine = MVELTemplateEngine.create().setExtension(".html")
        val gameDataPath = "e:/cdcq2_svr_gameserver_1305/gamedata/game-data/"

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
                    println("time changed by ${ctx.request().remoteAddress()}")
                }
                true
            }
            ctx.put("result", result)
            ctx.put("seconds", 60 - seconds)
            templateHandler("midnight")(ctx)
        }

        router.get("/reload_json/").handler(templateHandler("reload_json"))

        router.post("/reload_json/").handler { ctx ->
            val handler = templateHandler("reload_json")
            ctx.request().setExpectMultipart(true).uploadHandler { upload ->
                if (upload != null) {
                    val fileName = upload.filename()
                    val file = File(gameDataPath + fileName)
                    if (fileName != null && fileName.isNotEmpty() && file.exists()) {
                        println("$fileName changed by ${ctx.request().remoteAddress()}")
                        file.delete()
                        file.createNewFile()
                        upload.handler { buffer ->
                            file.appendBytes(buffer.bytes)
                        }
                        upload.endHandler {
                            handler(ctx.put("result", 2))
                        }
                    } else handler(ctx.put("result", -1))
                } else handler(ctx.put("result", 0))
            }
        }

        router.route("/gm").handler(templateHandler("gm"))
        router.route("/*").handler(templateHandler("gm"))
        router.route("/favicon.ico").handler(FaviconHandler.create("resource/favicon.ico"))
        vertx.createHttpServer().requestHandler(router::accept).listen(8090)
    }
}

