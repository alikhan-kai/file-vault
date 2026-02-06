package kz.kaspi.lab.filevault.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("file_metadata") // Маппинг на таблицу в реляционной базе (PostgreSQL/H2)
public class FileMetadata {
    @Id
    private Long id;            // Уникальный первичный ключ записи
    private String fileName;    // Оригинальное имя файла
    private String fileHash;    // SHA-256 отпечаток для дедупликации
    private String s3Url;       // Путь к объекту в хранилище MinIO (bucket/name)
    private Long fileSize;      // Размер файла в байтах
    private String status;      // Статус жизненного цикла (PROCESSING, COMPLETED, ERROR)
    private LocalDateTime createdAt; // Время регистрации файла в системе
}
