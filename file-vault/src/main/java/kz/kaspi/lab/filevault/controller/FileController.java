package kz.kaspi.lab.filevault.controller;

import kz.kaspi.lab.filevault.model.FileMetadata;
import kz.kaspi.lab.filevault.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;
    //Загрузка файла. Используем MULTIPART_FORM_DATA для передачи бинарных данных.
    //Возвращаем статус 202 (Accepted), так как процесс сохранения в S3 идет асинхронно.
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<String> upload(@RequestPart("file") FilePart filePart) {

        // Передаем файл в сервис для расчета хэша и дедупликации
        return fileService.initiateUpload(filePart)
                .map(result -> {
                    // Если сервис вернул строку о наличии дубликата
                    if (result.contains("уже существует")) {
                        return result;
                    }
                    // Если файл новый - выдаем хэш, по которому можно узнать статус позже
                    return "Запрос принят. Хэш для проверки статуса: " + result;
                });
    }

    //Получение информации о файле по его SHA-256 хэшу.
    //Позволяет узнать, завершилась ли загрузка в MinIO и какой у файла S3 URL.
    @GetMapping("/status/{hash}")
    public Mono<ResponseEntity<FileMetadata>> getStatus(@PathVariable String hash) {
        return fileService.getStatusByHash(hash)
                .map(ResponseEntity::ok) // Если нашли — 200 OK
                .defaultIfEmpty(ResponseEntity.notFound().build()); // Если нет — 404 Not Found
    }
}