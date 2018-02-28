package io.x1ao

import io.vertx.core.AbstractVerticle
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.mongo.MongoClient
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.ext.web.templ.MVELTemplateEngine
import io.x1ao.login.AuthRegister
import java.time.LocalDateTime


class MainVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val engine = MVELTemplateEngine.create().setExtension(".html")
        val client = MongoClient.createShared(vertx, JsonObject())

        val templateHandler = { templateFileName: String ->
            { ctx: RoutingContext ->
                engine.render(ctx, "templates/", templateFileName) { res ->
                    if (res.succeeded())
                        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
                    else
                        ctx.fail(res.cause())
                }
            }
        }


//        val authProvider = GithubAuth.create(vertx, "clientId", "clientSecret")
        val authProvider = AuthRegister(MongoClient.createShared(vertx, JsonObject()))

        val basicAuthHandle = BasicAuthHandler.create(authProvider)

        router.route().handler(CookieHandler.create())
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))
        router.route().handler(UserSessionHandler.create(authProvider))


        router.get("/register").handler { ctx ->
            if (ctx.user() == null)
                templateHandler("register")(ctx)
            else
                templateHandler("index")(ctx)
        }

        router.post("/register").handler { ctx ->
            if (ctx.user() != null)
                return@handler
            val request = ctx.request().setExpectMultipart(true)
            request.endHandler {
                val form = request.formAttributes()
                if (!form.isEmpty) {
                    val username = form.get("username") ?: ""
                    val password = form.get("password") ?: ""
                    val nickname = form.get("nickname") ?: ""
                    if (username.length >= 4 && password.length >= 8 && nickname.isNotEmpty()) {
                        val account = JsonObject().put("username", username).put("password", password).put("nickname", nickname)
                        authProvider.register(account)
                        templateHandler("index")(ctx)
                    } else ctx.fail(400)
                } else ctx.fail(400)
            }
        }

        router.route("/my/*").handler(basicAuthHandle)

        router.get("/my/article").handler(templateHandler("post_article"))

        router.post("/my/article").handler { ctx ->
            val user: User? = ctx.user()
            if (user == null) return@handler
            val accountInfo = user.principal()
            val request = ctx.request().setExpectMultipart(true)
            request.endHandler {
                val form = request.formAttributes()
                if (!form.isEmpty) {
                    val title = form.get("title") ?: ""
                    val content = form.get("content") ?: ""
                    if (title.isNotEmpty() && content.isNotEmpty()) {
                        val time = LocalDateTime.now()
                        val articleId = "${time.year % 100 - 8}${time.dayOfYear + 100}${(time.hour + 8) shl 1}${time.minute * 60 + time.second + 1000}${time.nano / 100_000_000}"
                        val document = JsonObject().put("articleId", articleId).put("author", accountInfo.getValue("username")).put("title", title).put("content", content)
                        client.save("articles", document) { res ->
                            if (res.succeeded())
                                templateHandler("article")(ctx.put("title", title).put("content", content))
                            else ctx.fail(404)
                        }
                    } else ctx.fail(400)
                } else ctx.fail(400)
            }
        }

        router.get("/:author/:articleId").handler { ctx ->
            val author = ctx.request().getParam("author")
            val articleId = ctx.request().getParam("articleId")
            val query = JsonObject().put("articleId", articleId).put("author", author)
            val fields = JsonObject()
            client.findOne("articles", query, fields) { res ->
                var found = false
                if (res.succeeded()) {
                    val article: JsonObject? = res.result()
                    if (article != null && !article.isEmpty) {
                        ctx.put("title", article.getString("title")).put("author", author).put("content", article.getString("content"))
                        found = true
                    }
                }
                if (found)
                    templateHandler("article")(ctx)
                else
                    ctx.fail(404)
            }
        }

        router.get("/articles").handler { ctx ->
            val query = JsonObject()
            client.find("articles", query) { res ->
                if (res.succeeded()) {
                    ctx.put("articles", res.result())
                    templateHandler("articles")(ctx)
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

