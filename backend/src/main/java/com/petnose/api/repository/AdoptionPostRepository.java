package com.petnose.api.repository;

import com.petnose.api.domain.entity.AdoptionPost;
import com.petnose.api.domain.enums.AdoptionPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface AdoptionPostRepository extends JpaRepository<AdoptionPost, Long> {

    boolean existsByDogIdAndStatusIn(String dogId, Collection<AdoptionPostStatus> statuses);

    @Query(
            value = """
                    select p
                    from AdoptionPost p
                    where p.status = :status
                    order by case when p.publishedAt is null then 1 else 0 end, p.publishedAt desc, p.id desc
                    """,
            countQuery = """
                    select count(p)
                    from AdoptionPost p
                    where p.status = :status
                    """
    )
    Page<AdoptionPost> findPublicPageByStatus(
            @Param("status") AdoptionPostStatus status,
            Pageable pageable
    );
}
