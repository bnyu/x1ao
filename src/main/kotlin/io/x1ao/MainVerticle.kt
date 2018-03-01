package io.x1ao

import io.vertx.core.AbstractVerticle
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonObject
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
                ctx.reroute("/")
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
                    if (username.length >= 4 && password.length >= 8) {
                        val account = JsonObject().put("username", username).put("password", password)
                        val future = authProvider.register(account)
                        future.setHandler { res ->
                            if (res.succeeded() && res.result())
                                templateHandler("index")(ctx.put("logged", true))
                            else
                                ctx.fail(401)
                        }
                    } else ctx.fail(400)
                } else ctx.fail(400)
            }
        }

        router.route("/login").handler(basicAuthHandle)
        router.route("/login").handler { ctx ->
            val logged = ctx.user() != null
            ctx.put("logged", logged)
            templateHandler("login")(ctx)
        }
        router.route("/my/*").handler(basicAuthHandle)

        router.route("/my").handler { ctx ->
            val user = ctx.user() ?: return@handler
            val query = user.principal()
            client.find("articles", query) { res ->
                if (res.succeeded()) {
                    ctx.put("articles", res.result())
                    templateHandler("articles")(ctx)
                } else {
                    ctx.fail(404)
                }
            }
        }

        router.get("/my/post_article").handler(templateHandler("post_article"))

        router.post("/my/post_article").handler { ctx ->
            val user = ctx.user() ?: return@handler
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
                        val document = accountInfo.put("articleId", articleId).put("title", title).put("content", content)
                        client.save("articles", document) { res ->
                            if (res.succeeded())
                                templateHandler("article")(ctx.put("title", title).put("author", accountInfo.getValue("author")).put("content", content))
                            else ctx.fail(404)
                        }
                    } else ctx.fail(400)
                } else ctx.fail(400)
            }
        }

        router.get("/author/:author/:articleId").handler { ctx ->
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

        val query = JsonObject()
        router.get("/articles").handler { ctx ->
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
        router.route("/").handler { ctx ->
            if (ctx.user() != null)
                ctx.put("logged", true)
            templateHandler("index")(ctx)
        }
        vertx.createHttpServer().requestHandler(router::accept).listen(8080)
    }
}

