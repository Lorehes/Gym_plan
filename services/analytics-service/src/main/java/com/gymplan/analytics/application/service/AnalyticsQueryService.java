package com.gymplan.analytics.application.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval;
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.gymplan.analytics.application.dto.AnalyticsPeriod;
import com.gymplan.analytics.application.dto.FrequencyDayData;
import com.gymplan.analytics.application.dto.PersonalRecordResponse;
import com.gymplan.analytics.application.dto.SummaryResponse;
import com.gymplan.analytics.application.dto.VolumeDataPoint;
import com.gymplan.analytics.domain.document.PersonalRecordDocument;
import com.gymplan.analytics.domain.document.SessionDocument;
import com.gymplan.analytics.domain.document.SetDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Elasticsearch Aggregation 기반 통계 집계 서비스.
 *
 * 성능 목표: 통계 API P95 < 500ms (docs/context/performance-goals.md)
 * 집계 전략: docs/specs/analytics-service.md §통계 집계 쿼리 전략
 */
@Service
public class AnalyticsQueryService {

    private static final IndexCoordinates SESSION_WILDCARD = IndexCoordinates.of("gymplan-sessions-*");
    private static final IndexCoordinates SET_WILDCARD = IndexCoordinates.of("gymplan-sets-*");
    private static final IndexCoordinates PR_INDEX = IndexCoordinates.of("gymplan-personal-records");

    private final ElasticsearchOperations esOps;

    public AnalyticsQueryService(ElasticsearchOperations esOps) {
        this.esOps = esOps;
    }

    public SummaryResponse getSummary(String userId, AnalyticsPeriod period) {
        String from = period.toEsDateMath();

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(List.of(
                        Query.of(f -> f.term(t -> t.field("userId").value(userId))),
                        Query.of(r -> r.range(rq -> rq.field("completedAt")
                                .gte(JsonData.of(from)).lte(JsonData.of("now/d"))))
                ))))
                .withAggregation("total_sessions",
                        Aggregation.of(a -> a.valueCount(vc -> vc.field("sessionId"))))
                .withAggregation("total_volume",
                        Aggregation.of(a -> a.sum(s -> s.field("totalVolume"))))
                .withAggregation("total_duration",
                        Aggregation.of(a -> a.sum(s -> s.field("durationSec"))))
                .withAggregation("avg_duration",
                        Aggregation.of(a -> a.avg(av -> av.field("durationSec"))))
                .withAggregation("top_muscle",
                        Aggregation.of(a -> a.terms(t -> t.field("muscleGroups").size(1))))
                .withMaxResults(0)
                .build();

        SearchHits<SessionDocument> hits = esOps.search(query, SessionDocument.class, SESSION_WILDCARD);

        if (!hits.hasAggregations()) {
            return emptySummary(period.name());
        }

        ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
        Map<String, ElasticsearchAggregation> aggsMap = aggs.aggregationsAsMap();

        long totalSessions = (long) aggsMap.get("total_sessions").aggregation().getAggregate().valueCount().value();
        double totalVolume = aggsMap.get("total_volume").aggregation().getAggregate().sum().value();
        long totalDuration = (long) aggsMap.get("total_duration").aggregation().getAggregate().sum().value();
        long avgDuration = (long) aggsMap.get("avg_duration").aggregation().getAggregate().avg().value();

        List<StringTermsBucket> muscleBuckets =
                aggsMap.get("top_muscle").aggregation().getAggregate().sterms().buckets().array();
        String topMuscle = muscleBuckets.isEmpty() ? null : muscleBuckets.get(0).key().stringValue();

        return new SummaryResponse(period.name(), totalSessions, totalVolume, totalDuration, avgDuration, topMuscle);
    }

    public List<VolumeDataPoint> getVolume(String userId, AnalyticsPeriod period, String muscle) {
        String from = period.toEsDateMath();

        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(f -> f.term(t -> t.field("userId").value(userId))));
        filters.add(Query.of(r -> r.range(rq -> rq.field("occurredAt")
                .gte(JsonData.of(from)).lte(JsonData.of("now/d")))));
        if (muscle != null && !muscle.isBlank()) {
            filters.add(Query.of(f -> f.term(t -> t.field("muscleGroup").value(muscle))));
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(filters)))
                .withAggregation("by_date", Aggregation.of(a -> a
                        .dateHistogram(dh -> dh
                                .field("occurredAt")
                                .calendarInterval(CalendarInterval.Day)
                                .format("yyyy-MM-dd"))
                        .aggregations("daily_volume", Aggregation.of(sub -> sub.sum(s -> s.field("volume"))))
                        .aggregations("muscle_terms", Aggregation.of(sub -> sub
                                .terms(t -> t.field("muscleGroup").size(10))
                                .aggregations("per_muscle_volume", Aggregation.of(mv -> mv.sum(s -> s.field("volume"))))))))
                .withMaxResults(0)
                .build();

        SearchHits<SetDocument> hits = esOps.search(query, SetDocument.class, SET_WILDCARD);

        if (!hits.hasAggregations()) {
            return List.of();
        }

        ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
        List<DateHistogramBucket> buckets =
                aggs.aggregationsAsMap().get("by_date").aggregation().getAggregate().dateHistogram().buckets().array();

        List<VolumeDataPoint> result = new ArrayList<>();
        for (DateHistogramBucket bucket : buckets) {
            String date = bucket.keyAsString();
            double vol = bucket.aggregations().get("daily_volume").sum().value();
            if (vol == 0.0) continue;

            if (muscle != null && !muscle.isBlank()) {
                result.add(new VolumeDataPoint(date, muscle, vol));
            } else {
                List<StringTermsBucket> muscleBuckets =
                        bucket.aggregations().get("muscle_terms").sterms().buckets().array();
                for (StringTermsBucket mb : muscleBuckets) {
                    double muscleVol = mb.aggregations().get("per_muscle_volume").sum().value();
                    result.add(new VolumeDataPoint(date, mb.key().stringValue(), muscleVol));
                }
            }
        }
        return result;
    }

    public Map<String, FrequencyDayData> getFrequency(String userId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        String from = ym.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String to = ym.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE);

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(List.of(
                        Query.of(f -> f.term(t -> t.field("userId").value(userId))),
                        Query.of(r -> r.range(rq -> rq.field("completedAt")
                                .gte(JsonData.of(from)).lte(JsonData.of(to))))
                ))))
                .withAggregation("by_day", Aggregation.of(a -> a
                        .dateHistogram(dh -> dh
                                .field("completedAt")
                                .calendarInterval(CalendarInterval.Day)
                                .format("yyyy-MM-dd"))
                        .aggregations("session_count", Aggregation.of(sub -> sub.valueCount(vc -> vc.field("sessionId"))))
                        .aggregations("daily_volume", Aggregation.of(sub -> sub.sum(s -> s.field("totalVolume"))))))
                .withMaxResults(0)
                .build();

        SearchHits<SessionDocument> hits = esOps.search(query, SessionDocument.class, SESSION_WILDCARD);

        if (!hits.hasAggregations()) {
            return Map.of();
        }

        ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
        List<DateHistogramBucket> buckets =
                aggs.aggregationsAsMap().get("by_day").aggregation().getAggregate().dateHistogram().buckets().array();

        Map<String, FrequencyDayData> result = new LinkedHashMap<>();
        for (DateHistogramBucket bucket : buckets) {
            long count = (long) bucket.aggregations().get("session_count").valueCount().value();
            if (count == 0L) continue;
            double vol = bucket.aggregations().get("daily_volume").sum().value();
            result.put(bucket.keyAsString(), new FrequencyDayData(count, vol));
        }
        return result;
    }

    public List<PersonalRecordResponse> getPersonalRecords(String userId) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field("userId").value(userId)))
                .withSort(Sort.by(Sort.Direction.DESC, "estimated1RM"))
                .withMaxResults(20)
                .build();

        SearchHits<PersonalRecordDocument> hits = esOps.search(query, PersonalRecordDocument.class, PR_INDEX);

        return hits.stream()
                .map(hit -> {
                    PersonalRecordDocument doc = hit.getContent();
                    return new PersonalRecordResponse(
                            doc.getExerciseId(),
                            doc.getExerciseName(),
                            doc.getMaxWeightKg(),
                            doc.getMaxReps(),
                            doc.getEstimated1RM(),
                            doc.getIsReliable(),
                            doc.getAchievedAt()
                    );
                })
                .collect(Collectors.toList());
    }

    private SummaryResponse emptySummary(String period) {
        return new SummaryResponse(period, 0, 0.0, 0, 0, null);
    }
}
