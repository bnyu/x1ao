package io.x1ao.yao

import io.vertx.core.AbstractVerticle
import io.vertx.core.http.HttpHeaders
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.FaviconHandler
import io.vertx.ext.web.templ.MVELTemplateEngine
import java.io.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*


class GMVerticle : AbstractVerticle() {
    override fun start() {
        val router = Router.router(vertx)
        val engine = MVELTemplateEngine.create().setExtension(".html")
        context.put("running", false)
        val config = BufferedInputStream(FileInputStream("config.properties"))
        val prop = Properties()
        prop.load(config)
        val timeModifiable = prop.getProperty("time_modifiable") == "true"
        val driverLetter = prop.getProperty("driver_letter") ?: "c:"
        val dataPath = prop.getProperty("data_path") ?: ""
        val runPath = prop.getProperty("run_path") ?: ""
        val jarName = prop.getProperty("jar_name") ?: ""
        val dataCenterJar = prop.getProperty("data_center_jar") ?: ""
        config.close()

        fun templateHandler(templateFileName: String) = { ctx: RoutingContext ->
            engine.render(ctx, "templates/gm/", templateFileName) { res ->
                if (res.succeeded())
                    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(res.result())
                else
                    ctx.fail(res.cause())
            }
        }

        val timeRoute0 = router.route("/gm/midnight").handler { ctx ->
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

        val timeRoute1 = router.get("/gm/set_date").handler { ctx ->
            val dateTime = LocalDateTime.now()
            ctx.put("year", dateTime.year).put("month", dateTime.monthValue).put("day", dateTime.dayOfMonth)
                .put("hour", dateTime.hour).put("minute", dateTime.minute)
            templateHandler("set_date")(ctx)
        }

        val timeRoute2 = router.post("/gm/set_date").handler { ctx ->
            ctx.request().setExpectMultipart(true).endHandler {
                val form = ctx.request().formAttributes()
                if (form != null && !form.isEmpty) {
                    val dateStr = form.get("date") ?: ""
                    if (dateStr.isNotEmpty()) {
                        val nowDate = LocalDate.now()
                        val newDate = LocalDate.parse(dateStr)
                        if (nowDate < newDate) {
                            Runtime.getRuntime().exec("cmd /c date $dateStr")
                            println("set date $dateStr by ${ctx.request().remoteAddress()}")
                        }
                    } else {
                        val timeStr = form.get("time") ?: ""
                        if (timeStr.isNotEmpty()) {
                            val nowTime = LocalTime.now()
                            val newTime = LocalTime.parse(timeStr)
                            if (nowTime < newTime) {
                                Runtime.getRuntime().exec("cmd /c time $timeStr")
                                println("set time $timeStr by ${ctx.request().remoteAddress()}")
                            }
                        }
                    }
                }
                ctx.reroute(HttpMethod.GET, "/gm/set_date")
            }
        }

        val dataRoute0 = router.get("/gm/reload_json").handler(templateHandler("reload_json"))

        val dataRoute1 = router.post("/gm/reload_json").handler { ctx ->
            val handler = templateHandler("reload_json")
            ctx.request().setExpectMultipart(true).uploadHandler { upload ->
                if (upload != null) {
                    val fileName = upload.filename() ?: ""
                    val file = File(dataPath + fileName)
                    if (fileName.isNotEmpty() && file.exists()) {
                        file.delete()
                        file.createNewFile()
                        upload.handler { buffer ->
                            file.appendBytes(buffer.bytes)
                        }
                        upload.endHandler {
                            handler(ctx.put("result", 2))
                            println("$fileName changed by ${ctx.request().remoteAddress()}")
                        }
                    } else handler(ctx.put("result", -1))
                } else handler(ctx.put("result", 0))
            }
        }

        val restartRoute0 = router.get("/gm/restart").handler { ctx ->
            ctx.put("state", context.get("running"))
            templateHandler("restart")(ctx)
        }

        val restartRoute1 = router.post("/gm/restart").handler { ctx ->
            ctx.request().bodyHandler { buffer ->
                val action = buffer.toString()
                when (action) {
                    "action=start\r\n" -> ctx.reroute("/gm/restart/start")
                    "action=shutdown\r\n" -> ctx.reroute("/gm/restart/shutdown")
                    else -> ctx.fail(400)
                }
            }
        }

        val restartRoute2 = router.route("/gm/restart/start").blockingHandler { ctx ->
            if (!context.get<Boolean>("running")) {
                context.put("running", true)
                ctx.put("running", true)
                if (dataCenterJar.isNotEmpty() && File(dataCenterJar).isFile) {
                    Runtime.getRuntime().exec("cmd /c $driverLetter && cd $runPath && javaw -jar $dataCenterJar")
                    try {
                        Thread.sleep(3000)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Runtime.getRuntime().exec("cmd /c $driverLetter && cd $runPath && javaw -jar $jarName")
                ctx.put("result", "start")
                templateHandler("restart")(ctx)
                println("game start by ${ctx.request().remoteAddress()}")
                try {
                    Thread.sleep(10000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                val cmd = Runtime.getRuntime().exec("cmd /c tasklist -fi \"imagename eq javaw.exe\"")
                val reader = BufferedReader(InputStreamReader(cmd.inputStream, "gbk"))
                try {
                    val i = reader.readLines().size
                    if (i <= 3) {
                        context.put("running", false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    reader.close()
                }
                ctx.reroute(HttpMethod.GET, "/gm/restart")
            }
        }

        val restartRoute3 = router.route("/gm/restart/shutdown").blockingHandler { ctx ->
            if (context.get<Boolean>("running")) {
                context.put("running", false)

                val cmd = Runtime.getRuntime().exec("cmd /c tasklist -fi \"imagename eq javaw.exe\"")
                val reader = BufferedReader(InputStreamReader(cmd.inputStream, "gbk"))
                try {
                    var i = 0
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (i >= 3 && line.isNotEmpty()) {
                            val list = line.split(' ', ignoreCase = true).filter { it.isNotEmpty() }
                            val pid = list[1].toInt()
                            Runtime.getRuntime().exec("cmd /c taskkill /$pid -t -f")
                            ctx.put("result", "shutdown")
                        }
                        line = reader.readLine()
                        i += 1
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    reader.close()
                }

                templateHandler("restart")(ctx)
                println("game shutdown by ${ctx.request().remoteAddress()}")
                try {
                    Thread.sleep(10000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else ctx.reroute(HttpMethod.GET, "/gm/restart")
        }

        router.route("/favicon.ico").handler(FaviconHandler.create("resource/favicon.ico"))

        if (!timeModifiable) {
            timeRoute0.disable()
            timeRoute1.disable()
            timeRoute2.disable()
        }
        val dataModifiable = if (dataPath.isEmpty() || !File(dataPath).isDirectory) {
            dataRoute0.disable()
            dataRoute1.disable()
            false
        } else true
        val restartable = if (runPath.isEmpty() || jarName.isEmpty() || !File(runPath).isDirectory || !File(jarName).isFile) {
            restartRoute0.disable()
            restartRoute1.disable()
            restartRoute2.disable()
            restartRoute3.disable()
            false
        } else true

        router.route("/gm").handler { ctx ->
            ctx.put("t", timeModifiable).put("d", dataModifiable).put("r", restartable)
            templateHandler("index")(ctx)
        }

        router.route("/*").handler { ctx -> ctx.reroute("/gm") }

        if (timeModifiable || dataModifiable || restartable) {
            vertx.createHttpServer().requestHandler(router::accept).listen(8090)
            println("started. listen port: 8090")
        } else {
            vertx.undeploy(GMVerticle::class.java.name)
            println("config.properties error")
        }
    }
}

