package kz.kaspi.lab.filevault.repository;

import kz.kaspi.lab.filevault.model.FileMetadata;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface FileMetadataRepository extends ReactiveCrudRepository<FileMetadata, Long> {
    //Поиск метаданных по уникальному хэшу содержимого.
    //Используется для предотвращения повторной загрузки идентичных файлов.
    Mono<FileMetadata> findByFileHash(String fileHash);
}
