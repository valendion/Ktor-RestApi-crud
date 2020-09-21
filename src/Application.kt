package com.example

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.gson.*
import io.ktor.features.*
import io.ktor.http.cio.Response
import io.ktor.request.receive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.jodatime.Date
import org.jetbrains.exposed.sql.jodatime.date
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(ContentNegotiation) {
        gson {
        }
    }

    Database.connect(
        "jdbc:mysql://localhost:3306/dbktor", driver = "com.mysql.jdbc.Driver",
        user = "root", password = ""
    )



    routing {
        get("/") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }

        get("/json/gson") {
            call.respond(mapOf("hello" to "world"))
        }

        //menampilkan semua data
        get("/todo") {
            val todos = getAllTodos()
            call.respond(todos)
        }

        //{} karna idnya dinamis
        get("/todo/{id}") {
            //parameter selalu tipe data string jdi di konvert ke toInt()
            val id = call.parameters["id"]?.toInt() ?: 0

            val todo = getTodolistById(id)
            //jika todolist null / data kosong maka akan muncul tampilan not found 404
            if (todo == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(todo)
        }

        get("/note") {
            val notes = listOf(
                Note("Important", "Explain about Human"),
                Note("Dictionary 1001", "Explain about meaning")
            )
            call.respond(mapOf("data" to notes))
        }

        //insert memakai post
        post("/todo") {
            //kan field id itu
            val todo = call.receive<newTodolist>()

            val insert = addTodoList(todo)
            if (insert == null) call.respond(HttpStatusCode.InternalServerError)
            else call.respond(insert)
        }

        put("/todo/{id}") {
            val id = call.parameters["id"]?.toInt() ?: 0
            val todo = call.receive<newTodolist>()
            val updated = updateTodoList(id, todo)
            if (updated == null) call.respond(HttpStatusCode.NotFound)
            else call.respond(updated)
        }
        
        delete("/todo/{id}"){
            val id = call.parameters["id"]?.toInt() ?: 0
            val delete = deleteTodolist(id)
            if (delete) call.respond(Response(message="Delete successful"))
            else call.respond(HttpStatusCode.NotFound, "Delete Failed")
        }


    }
}

suspend fun deleteTodolist(id: Int): Boolean {

    return withContext(Dispatchers.IO){
        transaction {
            TodoTable.deleteWhere { TodoTable.id eq id } > 0
        }
    }

}

suspend fun updateTodoList(id: Int, todo: newTodolist): Todolist? {

    return withContext(Dispatchers.IO){
        transaction {
             TodoTable.update({TodoTable.id eq id}) {
                it[name] = todo.name
                it[date] = DateTime.parse(todo.date)
                it[done] = todo.done
            }
        }
        return@withContext getTodolistById(id)
    }

}

suspend fun addTodoList(todo: newTodolist): Todolist? {

    var todoId = 0

    return withContext(Dispatchers.IO) {
        transaction {
            todoId = TodoTable.insert {
                //data yang ingin di insert
                //diambil dari data class NewTodolist
                it[name] = todo.name
                it[date] = DateTime.parse(todo.date)
                it[done] = todo.done
            } get TodoTable.id
        }
        return@withContext getTodolistById(todoId)
    }

}

//Todolist? karna bisa idnya bisa saja tidak ketemu

suspend fun getTodolistById(id: Int): Todolist? {
    return withContext(Dispatchers.IO) {
        //datanya harus di jalankan di backgroud
        transaction {
            //perintah tabel
            //mapNotNull karne maping dan juga karna kemungkinan data id bernilai null atau kosong
            TodoTable.select { TodoTable.id eq id }.mapNotNull { it.toModel() }.singleOrNull()
        }
    }
}

suspend fun getAllTodos(): List<Todolist> {
    return withContext(Dispatchers.IO) {
        //trus dibalikin lagi ke main threadnya
        transaction {
            //datanya dijalankan di background
            TodoTable.selectAll().map { it.toModel() }
        }
    }
}

object TodoTable : Table("todo") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val date = date("date")
    val done = bool("done")


    override val primaryKey = PrimaryKey(id) // name is optional here
}

data class Todolist(
    val id: Int,
    val name: String,
    val date: String,
    val done: Boolean
)

data class newTodolist(
    val name: String,
    val date: String,
    val done: Boolean
)

data class Note(
    val header: String,
    val body: String
)

fun ResultRow.toModel(): Todolist {
    return Todolist(
        this[TodoTable.id],
        this[TodoTable.name],
        this[TodoTable.date].toString(),
        this[TodoTable.done]
    )
}

data class Response(val message: String)

