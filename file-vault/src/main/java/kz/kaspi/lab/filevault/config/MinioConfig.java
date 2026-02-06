package kz.kaspi.lab.filevault.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {
    // Считываем параметры подключения из application.properties
    @Value("${minio.endpoint}")
    private String endpoint; // Адрес сервера MinIO (например, http://localhost:9000)

    @Value("${minio.accessKey}")
    private String accessKey; // Логин для доступа

    @Value("${minio.secretKey}")
    private String secretKey; // Пароль для доступа

    //Создаем Bean MinioClient.
    //Это основной инструмент (SDK), через который сервис будет отправлять файлы в S3.
    @Bean
    public MinioClient minioClient(){
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}