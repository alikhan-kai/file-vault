package kz.kaspi.lab.filevault.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import kz.kaspi.lab.filevault.model.FileMetadata;
import kz.kaspi.lab.filevault.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import org.springframework.core.io.buffer.DataBufferUtils;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;


import java.time.LocalDateTime;


@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private final MinioClient minioClient;
    private final FileMetadataRepository repository;
    private final org.springframework.data.redis.core.ReactiveRedisTemplate<String, String> redisTemplate;

    @Value("${minio.bucket}")
    private String bucket;
    //Инициализация загрузки: расчет хэша и проверка на дубликаты.
    public Mono<String> initiateUpload(FilePart filePart) {
        String fileName = filePart.filename();

        // Считываем контент файла в буфер для расчета хэша
        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer); // Очистка памяти

                    try {
                        // Генерируем уникальный SHA-256 отпечаток файла
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        String fileHash = HexFormat.of().formatHex(digest.digest(bytes));

                        // 1. Проверяем в Redis, не загружался ли такой файл ранее
                        return redisTemplate.opsForValue().get(fileHash)
                                .flatMap(status -> {
                                    log.info("Файл с хэшем {} уже известен системе", fileHash);
                                    return Mono.just("Файл уже обрабатывается или существует. Хэш/ID: " + status);
                                })
                                .switchIfEmpty(
                                        // 2. Если файл новый — помечаем в Redis "в процессе" и запускаем загрузку в фоне
                                        redisTemplate.opsForValue().set(fileHash, "PROCESSING")
                                                .then(Mono.fromRunnable(() -> startBackgroundProcess(bytes, fileName, fileHash)))
                                                .thenReturn(fileHash)
                                );
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    //Запуск тяжелого процесса загрузки в отдельном потоке (boundedElastic),
    //чтобы не блокировать основной поток приложения.
    private void startBackgroundProcess(byte[] bytes, String fileName, String fileHash) {
        saveAndUpload(bytes, fileName, (long) bytes.length, fileHash)
                ///
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(meta -> log.info("Фоновая загрузка завершена для: {}", fileName))
                .doOnError(e -> {
                    log.error("Ошибка при загрузке: {}", e.getMessage());
                    // В случае сбоя удаляем флаг из Redis, чтобы разрешить повторную попытку
                    redisTemplate.opsForValue().delete(fileHash).subscribe();
                })
                .subscribe(); // Асинхронный запуск
    }

    //Сохранение метаданных в БД и самого файла в объектное хранилище MinIO (S3).
    private Mono<FileMetadata> saveAndUpload(byte[] bytes, String fileName, long fileSize, String fileHash) {
        return repository.save(FileMetadata.builder()
                        .fileName(fileName)
                        .fileSize(fileSize)
                        .fileHash(fileHash)
                        .status("UPLOADING")
                        .createdAt(LocalDateTime.now())
                        .build())
                .flatMap(savedMeta -> Mono.fromCallable(() -> {
                    // 3. Отправка байтов в бакет MinIO
                    try (InputStream is = new java.io.ByteArrayInputStream(bytes)) {
                        minioClient.putObject(PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(fileName)
                                .stream(is, (long) bytes.length, -1)
                                .build());
                    }
                    return savedMeta;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(meta -> {
                    // Обновляем статус в БД после успешной загрузки в S3
                    meta.setStatus("COMPLETED");
                    meta.setS3Url(bucket + "/" + fileName);
                    return repository.save(meta);
                })
                .flatMap(finalMeta ->
                        // 4. Фиксируем в Redis финальный ID вместо временного статуса
                        redisTemplate.opsForValue().set(fileHash, finalMeta.getId().toString())
                                .thenReturn(finalMeta)
                );
    }

    public Mono<FileMetadata> getStatusByHash(String hash) {
        return repository.findByFileHash(hash);
    }
}