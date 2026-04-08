# Exercise Catalog API

**Base**: `/api/v1/exercises`
**Port**: 8083
**DB**: MySQL (`gymplan_exercise`), Elasticsearch

---

## GET /api/v1/exercises

운동 종목 검색 (Elasticsearch 기반)

**Query Params**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `q` | String | 검색어 (이름) |
| `muscle` | String | 부위 (CHEST/BACK/SHOULDERS/ARMS/LEGS/CORE/CARDIO) |
| `equipment` | String | 장비 (BARBELL/DUMBBELL/MACHINE/CABLE/BODYWEIGHT/BAND) |
| `page` | Int | 페이지 (기본 0) |
| `size` | Int | 크기 (기본 20) |

**Response 200**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "exerciseId":  10,
        "name":        "벤치프레스",
        "nameEn":      "Bench Press",
        "muscleGroup": "CHEST",
        "equipment":   "BARBELL",
        "difficulty":  "INTERMEDIATE"
      }
    ],
    "totalElements": 240
  }
}
```

---

## GET /api/v1/exercises/{exerciseId}

종목 상세 조회 (description, videoUrl 포함)

---

## POST /api/v1/exercises

커스텀 종목 생성 (`is_custom=true`, `created_by={userId}`)

---

## GET /api/v1/exercises/muscle-groups

부위 목록 반환 (인증 불필요)
