package com.example.routes.capsule.route

import com.example.common.exception.CustomException
import com.example.common.exception.ErrorCode
import com.example.common.file.FileHandler
import com.example.common.utils.FormatVerify
import com.example.common.utils.getWithBinding
import com.example.common.utils.postWithBinding
import com.example.routes.capsule.service.CapsuleService
import com.example.routes.capsule.types.CapsuleDetailRequest
import com.example.routes.capsule.types.CreateNewCapsuleRequest
import com.example.security.Intercept
import com.example.security.PasetoProvider
import com.example.security.TimeBaseEncryptionProvider
import com.example.types.response.GlobalResponseProvider
import com.example.types.storage.ContentType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.get

fun Route.v1CapsuleRoute() {
    val service = get<CapsuleService>()

    route("/capsule") {
        intercept(ApplicationCallPipeline.Call, Intercept.tokenHeaderVerify())

        postWithBinding<CreateNewCapsuleRequest>("/create") { req ->
            FormatVerify.verifyEmailFormat(req.recipients)

            try {
                val exist = service.verifyEmailExist(req.recipients)
                if (!exist) {
                    call.respond(HttpStatusCode.BadRequest, GlobalResponseProvider.new(-1, "not exist email : ${req.recipients}", null))
                    return@postWithBinding
                }
            } catch (e : Exception) {
                throw CustomException(ErrorCode.LOGIC_EXCEPTIOn, e.message)
            }

            if (!FormatVerify.validateFutureDate(req.scheduledOpenDate)) {
                call.respond(HttpStatusCode.BadRequest, GlobalResponseProvider.new(-1, "schedule open data too low", null))
                return@postWithBinding
            }

            val token = call.request.headers["Authorization"].toString() // -> intercept 통해서 검증한 값이기 떄문에 바로 사용 가능
            val userID =  PasetoProvider.getUserId(token)

            when(val type = ContentType.valueOf(req.contentType)) {
                ContentType.text -> {
                    if (req.content.isEmpty()) {
                        call.respond(HttpStatusCode.BadRequest, GlobalResponseProvider.new(-1, "content must provided", null))
                    }


//                    TimeBaseEncryptionProvider.encryptWithTimelock(req.content)
                    val response = service.handlingTextContent(
                        type,
                        req.recipients, userID, req.title, req.content, req.description,
                        req.scheduledOpenDate
                    )

                    call.respond(HttpStatusCode.OK, response)
                }
                ContentType.audio , ContentType.video, ContentType.image  -> {
                    val multipart = call.receiveMultipart()
                    val fileItem = FileHandler.exportFileData(multipart)

                    val (fileName, fileData) = FileHandler.handlingIncomingFile(fileItem)

                    val response = service.handlingFileContent(
                        type,
                        req.recipients, userID,
                        req.title, req.content, req.description, req.scheduledOpenDate,
                        fileData, fileName
                    )

                    call.respond(HttpStatusCode.Created, response)
                }
            }
        }

        getWithBinding<CapsuleDetailRequest>("/capsule-detail/{capsuleId}") { req ->
            val response = service.getCapsuleContentById(req.capsuleId)
            call.respond(HttpStatusCode.OK, response)
        }

    }
}
