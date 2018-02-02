package io.x1ao.yao

import io.vertx.core.AbstractVerticle
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.templ.MVELTemplateEngine
import java.time.LocalTime

class MainVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val engine = MVELTemplateEngine.create().setExtension(".html")
        val client = MongoClient.createShared(vertx, JsonObject())

        var index = 100 //temp
        fun incIndex() = ++index

        fun templateHandler(templateFileName: String) = { ctx: RoutingContext ->
            engine.render(ctx, "templates/", templateFileName) { res ->
                if (res.succeeded())
                    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
                else
                    ctx.fail(res.cause())
            }
        }

        router.get("/article/:articleId").handler { ctx ->
            val articleId = ctx.request().getParam("articleId")
            val query = JsonObject().put("articleId", articleId)
            val fields = JsonObject()
            client.findOne("articles", query, fields) { res ->
                var found = false
                if (res.succeeded()) {
                    val article: JsonObject? = res.result()
                    if (article != null && !article.isEmpty) {
                        ctx.put("title", article.getString("title")).put("content", article.getString("content"))
                        found = true
                    }
                }
                if (found)
                    templateHandler("article").invoke(ctx)
                else
                    ctx.fail(404)
            }
        }

        router.get("/post_article").handler(templateHandler("post_article"))

        router.post("/post_article").handler { ctx ->
            val request = ctx.request().setExpectMultipart(true)
            request.endHandler {
                val form = request.formAttributes()
                if (!form.isEmpty) {
                    val title = form.get("title") ?: ""
                    val content = form.get("content") ?: ""
                    if (title.isNotEmpty() && content.isNotEmpty()) {
                        val document = JsonObject().put("articleId", "${incIndex()}").put("title", title).put("content", content)
                        client.save("articles", document) { res ->
                            if (res.succeeded())
                                templateHandler("article").invoke(ctx.put("title", title).put("content", content))
                             else ctx.fail(404)
                        }
                    } else ctx.fail(400)
                } else ctx.fail(400)
            }
        }

        router.get("/articles").handler { ctx ->
            val query = JsonObject()
            client.find("articles", query) { res ->
                if (res.succeeded()) {
                    ctx.put("articles", res.result())
                    templateHandler("articles").invoke(ctx)
                } else {
                    ctx.fail(404)
                }
            }
        }

        router.route("/favicon.ico").handler(FaviconHandler.create("resource/favicon.ico"))
        router.route("/").handler(templateHandler("index"))
        vertx.createHttpServer().requestHandler(router::accept).listen(8080)
    }
}

