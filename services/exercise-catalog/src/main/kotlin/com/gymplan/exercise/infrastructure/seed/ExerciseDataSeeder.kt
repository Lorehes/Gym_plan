package com.gymplan.exercise.infrastructure.seed

import com.gymplan.exercise.domain.entity.Exercise
import com.gymplan.exercise.domain.repository.ExerciseRepository
import com.gymplan.exercise.domain.vo.Difficulty
import com.gymplan.exercise.domain.vo.Equipment
import com.gymplan.exercise.domain.vo.MuscleGroup
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("local", "dev")
class ExerciseDataSeeder(
    private val exerciseRepository: ExerciseRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (exerciseRepository.count() > 0) {
            log.info("Exercise data already exists (count={}), skipping seed", exerciseRepository.count())
            return
        }

        val exercises = buildSeedData()
        exerciseRepository.saveAll(exercises)
        log.info("Seeded {} exercises", exercises.size)
    }

    private fun buildSeedData(): List<Exercise> = listOf(
        // ──────────────────────────────────────────
        // CHEST (15)
        // ──────────────────────────────────────────
        exercise("바벨 벤치프레스", "Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "플랫 벤치에 누워 바벨을 가슴까지 내렸다 밀어올리는 동작"),
        exercise("인클라인 바벨 벤치프레스", "Incline Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "30~45도 인클라인 벤치에서 수행하는 벤치프레스"),
        exercise("디클라인 바벨 벤치프레스", "Decline Barbell Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "디클라인 벤치에서 수행하여 하부 흉근을 타겟하는 벤치프레스"),
        exercise("덤벨 벤치프레스", "Dumbbell Bench Press", MuscleGroup.CHEST, Equipment.DUMBBELL, Difficulty.INTERMEDIATE,
            "플랫 벤치에 누워 양손에 덤벨을 들고 수행하는 프레스"),
        exercise("인클라인 덤벨 벤치프레스", "Incline Dumbbell Bench Press", MuscleGroup.CHEST, Equipment.DUMBBELL, Difficulty.INTERMEDIATE,
            "인클라인 벤치에서 덤벨로 수행하는 프레스"),
        exercise("덤벨 플라이", "Dumbbell Fly", MuscleGroup.CHEST, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "플랫 벤치에 누워 양팔을 벌렸다 모으는 동작"),
        exercise("인클라인 덤벨 플라이", "Incline Dumbbell Fly", MuscleGroup.CHEST, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "인클라인 벤치에서 수행하는 덤벨 플라이"),
        exercise("케이블 크로스오버", "Cable Crossover", MuscleGroup.CHEST, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블 머신 양쪽 하이 풀리를 사용하여 가슴 앞에서 교차하는 동작"),
        exercise("로우 케이블 크로스오버", "Low Cable Crossover", MuscleGroup.CHEST, Equipment.CABLE, Difficulty.BEGINNER,
            "로우 풀리에서 위로 당기며 상부 흉근을 타겟하는 크로스오버"),
        exercise("체스트 프레스 머신", "Chest Press Machine", MuscleGroup.CHEST, Equipment.MACHINE, Difficulty.BEGINNER,
            "머신에 앉아 핸들을 밀어내는 프레스 동작"),
        exercise("펙덱 플라이", "Pec Deck Fly", MuscleGroup.CHEST, Equipment.MACHINE, Difficulty.BEGINNER,
            "펙덱 머신에 앉아 양팔을 모으는 플라이 동작"),
        exercise("푸시업", "Push-up", MuscleGroup.CHEST, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "엎드려 팔을 굽혔다 펴는 기본 가슴 운동"),
        exercise("딥스", "Dips", MuscleGroup.CHEST, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "평행봉에서 상체를 앞으로 기울이며 수행하는 딥스"),
        exercise("클로즈그립 벤치프레스", "Close-Grip Bench Press", MuscleGroup.CHEST, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "좁은 그립으로 수행하여 내측 흉근과 삼두근을 함께 자극"),
        exercise("밴드 체스트 프레스", "Band Chest Press", MuscleGroup.CHEST, Equipment.BAND, Difficulty.BEGINNER,
            "저항 밴드를 등 뒤로 걸고 앞으로 밀어내는 프레스"),

        // ──────────────────────────────────────────
        // BACK (15)
        // ──────────────────────────────────────────
        exercise("바벨 데드리프트", "Barbell Deadlift", MuscleGroup.BACK, Equipment.BARBELL, Difficulty.ADVANCED,
            "바닥에서 바벨을 들어올리는 전신 복합 운동"),
        exercise("바벨 로우", "Barbell Row", MuscleGroup.BACK, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "상체를 숙이고 바벨을 복부 쪽으로 당기는 동작"),
        exercise("펜들레이 로우", "Pendlay Row", MuscleGroup.BACK, Equipment.BARBELL, Difficulty.ADVANCED,
            "매 반복 바닥에서 시작하는 엄격한 바벨 로우"),
        exercise("덤벨 원암 로우", "Dumbbell One-Arm Row", MuscleGroup.BACK, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "벤치에 한 손을 짚고 반대쪽 손으로 덤벨을 당기는 동작"),
        exercise("덤벨 로우", "Dumbbell Row", MuscleGroup.BACK, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "양손에 덤벨을 들고 상체를 숙여 당기는 로우"),
        exercise("시티드 케이블 로우", "Seated Cable Row", MuscleGroup.BACK, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블 머신에 앉아 로우 풀리를 몸 쪽으로 당기는 동작"),
        exercise("랫 풀다운", "Lat Pulldown", MuscleGroup.BACK, Equipment.CABLE, Difficulty.BEGINNER,
            "와이드 바를 쇄골 방향으로 당겨 내리는 동작"),
        exercise("클로즈그립 랫 풀다운", "Close-Grip Lat Pulldown", MuscleGroup.BACK, Equipment.CABLE, Difficulty.BEGINNER,
            "좁은 그립으로 수행하는 랫 풀다운"),
        exercise("페이스 풀", "Face Pull", MuscleGroup.BACK, Equipment.CABLE, Difficulty.BEGINNER,
            "로프 어태치먼트를 얼굴 높이로 당기는 후면 삼각근/상부 등 운동"),
        exercise("T-바 로우", "T-Bar Row", MuscleGroup.BACK, Equipment.MACHINE, Difficulty.INTERMEDIATE,
            "T-바 머신을 사용한 로우 동작"),
        exercise("머신 로우", "Machine Row", MuscleGroup.BACK, Equipment.MACHINE, Difficulty.BEGINNER,
            "로우 머신에 앉아 수행하는 등 운동"),
        exercise("풀업", "Pull-up", MuscleGroup.BACK, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "철봉에 매달려 몸을 끌어올리는 동작"),
        exercise("친업", "Chin-up", MuscleGroup.BACK, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "언더그립으로 수행하는 풀업"),
        exercise("인버티드 로우", "Inverted Row", MuscleGroup.BACK, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "낮은 바에 매달려 몸을 당기는 수평 풀업"),
        exercise("밴드 풀 어파트", "Band Pull Apart", MuscleGroup.BACK, Equipment.BAND, Difficulty.BEGINNER,
            "밴드를 양손으로 잡고 양옆으로 벌려 후면 삼각근과 상부 등을 자극"),

        // ──────────────────────────────────────────
        // SHOULDERS (14)
        // ──────────────────────────────────────────
        exercise("바벨 오버헤드 프레스", "Barbell Overhead Press", MuscleGroup.SHOULDERS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "서서 바벨을 머리 위로 밀어올리는 프레스"),
        exercise("비하인드 넥 프레스", "Behind the Neck Press", MuscleGroup.SHOULDERS, Equipment.BARBELL, Difficulty.ADVANCED,
            "바벨을 목 뒤에서 머리 위로 밀어올리는 프레스"),
        exercise("바벨 업라이트 로우", "Barbell Upright Row", MuscleGroup.SHOULDERS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "바벨을 턱 아래까지 수직으로 당기는 동작"),
        exercise("덤벨 숄더 프레스", "Dumbbell Shoulder Press", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, Difficulty.INTERMEDIATE,
            "앉아서 덤벨을 머리 위로 밀어올리는 프레스"),
        exercise("아놀드 프레스", "Arnold Press", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, Difficulty.INTERMEDIATE,
            "회전하며 밀어올리는 덤벨 숄더 프레스 변형"),
        exercise("덤벨 사이드 레터럴 레이즈", "Dumbbell Lateral Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "양손에 덤벨을 들고 옆으로 들어올리는 동작"),
        exercise("덤벨 프론트 레이즈", "Dumbbell Front Raise", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "덤벨을 정면으로 들어올리는 동작"),
        exercise("덤벨 리어 델트 플라이", "Dumbbell Rear Delt Fly", MuscleGroup.SHOULDERS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "상체를 숙이고 덤벨을 옆으로 들어올려 후면 삼각근 자극"),
        exercise("케이블 레터럴 레이즈", "Cable Lateral Raise", MuscleGroup.SHOULDERS, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블을 사용하여 옆으로 들어올리는 레터럴 레이즈"),
        exercise("케이블 리어 델트 플라이", "Cable Rear Delt Fly", MuscleGroup.SHOULDERS, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블을 사용한 후면 삼각근 플라이"),
        exercise("숄더 프레스 머신", "Shoulder Press Machine", MuscleGroup.SHOULDERS, Equipment.MACHINE, Difficulty.BEGINNER,
            "머신에 앉아 수행하는 숄더 프레스"),
        exercise("리버스 펙덱", "Reverse Pec Deck", MuscleGroup.SHOULDERS, Equipment.MACHINE, Difficulty.BEGINNER,
            "펙덱 머신을 반대로 앉아 후면 삼각근을 자극하는 동작"),
        exercise("파이크 푸시업", "Pike Push-up", MuscleGroup.SHOULDERS, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "엉덩이를 높이 올린 자세에서 수행하는 어깨 중심 푸시업"),
        exercise("핸드스탠드 푸시업", "Handstand Push-up", MuscleGroup.SHOULDERS, Equipment.BODYWEIGHT, Difficulty.ADVANCED,
            "물구나무 자세에서 수행하는 푸시업"),

        // ──────────────────────────────────────────
        // ARMS (16)
        // ──────────────────────────────────────────
        exercise("바벨 컬", "Barbell Curl", MuscleGroup.ARMS, Equipment.BARBELL, Difficulty.BEGINNER,
            "바벨을 언더그립으로 잡고 컬하는 이두 운동"),
        exercise("EZ바 컬", "EZ-Bar Curl", MuscleGroup.ARMS, Equipment.BARBELL, Difficulty.BEGINNER,
            "EZ바를 이용한 이두 컬"),
        exercise("바벨 프리쳐 컬", "Barbell Preacher Curl", MuscleGroup.ARMS, Equipment.BARBELL, Difficulty.BEGINNER,
            "프리쳐 벤치에서 수행하는 바벨 이두 컬"),
        exercise("덤벨 컬", "Dumbbell Curl", MuscleGroup.ARMS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "덤벨을 사용한 기본 이두 컬"),
        exercise("덤벨 해머 컬", "Dumbbell Hammer Curl", MuscleGroup.ARMS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "중립 그립으로 수행하여 상완근과 이두를 함께 자극"),
        exercise("덤벨 인클라인 컬", "Incline Dumbbell Curl", MuscleGroup.ARMS, Equipment.DUMBBELL, Difficulty.INTERMEDIATE,
            "인클라인 벤치에 기대어 수행하는 이두 컬"),
        exercise("덤벨 컨센트레이션 컬", "Dumbbell Concentration Curl", MuscleGroup.ARMS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "앉아서 팔꿈치를 무릎 안쪽에 고정하고 수행하는 컬"),
        exercise("케이블 컬", "Cable Curl", MuscleGroup.ARMS, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블 로우 풀리를 사용한 이두 컬"),
        exercise("바벨 스컬 크러셔", "Barbell Skull Crusher", MuscleGroup.ARMS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "벤치에 누워 바벨을 이마 방향으로 내렸다 올리는 삼두 운동"),
        exercise("클로즈그립 벤치프레스 (삼두)", "Close-Grip Bench Press (Triceps)", MuscleGroup.ARMS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "좁은 그립 벤치프레스로 삼두를 주타겟으로 수행"),
        exercise("덤벨 오버헤드 트라이셉 익스텐션", "Dumbbell Overhead Tricep Extension", MuscleGroup.ARMS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "덤벨을 머리 위에서 팔꿈치를 접어 내렸다 올리는 삼두 운동"),
        exercise("덤벨 킥백", "Dumbbell Kickback", MuscleGroup.ARMS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "상체를 숙이고 팔꿈치를 고정한 채 덤벨을 뒤로 밀어내는 삼두 운동"),
        exercise("케이블 트라이셉 푸시다운", "Cable Tricep Pushdown", MuscleGroup.ARMS, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블 하이 풀리를 아래로 밀어내리는 삼두 운동"),
        exercise("케이블 오버헤드 트라이셉 익스텐션", "Cable Overhead Tricep Extension", MuscleGroup.ARMS, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블 로우 풀리를 등 뒤에서 머리 위로 펴올리는 삼두 운동"),
        exercise("트라이셉 딥스", "Tricep Dips", MuscleGroup.ARMS, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "상체를 세우고 수행하여 삼두를 주타겟으로 하는 딥스"),
        exercise("덤벨 리스트 컬", "Dumbbell Wrist Curl", MuscleGroup.ARMS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "전완근 강화를 위한 손목 컬"),

        // ──────────────────────────────────────────
        // LEGS (18)
        // ──────────────────────────────────────────
        exercise("바벨 백스쿼트", "Barbell Back Squat", MuscleGroup.LEGS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "바벨을 등에 얹고 수행하는 기본 스쿼트"),
        exercise("바벨 프론트 스쿼트", "Barbell Front Squat", MuscleGroup.LEGS, Equipment.BARBELL, Difficulty.ADVANCED,
            "바벨을 쇄골 앞에 얹고 수행하는 스쿼트"),
        exercise("바벨 런지", "Barbell Lunge", MuscleGroup.LEGS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "바벨을 등에 얹고 한 발씩 앞으로 나가며 수행하는 런지"),
        exercise("바벨 루마니안 데드리프트", "Barbell Romanian Deadlift", MuscleGroup.LEGS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "무릎을 살짝 굽힌 채 상체를 숙여 햄스트링을 자극하는 데드리프트"),
        exercise("바벨 힙 스러스트", "Barbell Hip Thrust", MuscleGroup.LEGS, Equipment.BARBELL, Difficulty.INTERMEDIATE,
            "벤치에 등을 기대고 바벨을 골반 위에 얹어 엉덩이로 밀어올리는 동작"),
        exercise("덤벨 고블릿 스쿼트", "Dumbbell Goblet Squat", MuscleGroup.LEGS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "덤벨을 가슴 앞에 안고 수행하는 스쿼트"),
        exercise("덤벨 런지", "Dumbbell Lunge", MuscleGroup.LEGS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "양손에 덤벨을 들고 수행하는 런지"),
        exercise("덤벨 불가리안 스플릿 스쿼트", "Dumbbell Bulgarian Split Squat", MuscleGroup.LEGS, Equipment.DUMBBELL, Difficulty.INTERMEDIATE,
            "뒷발을 벤치에 올리고 수행하는 한 다리 스쿼트"),
        exercise("덤벨 스텝업", "Dumbbell Step-up", MuscleGroup.LEGS, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "덤벨을 들고 박스 위로 올라서는 동작"),
        exercise("레그 프레스", "Leg Press", MuscleGroup.LEGS, Equipment.MACHINE, Difficulty.BEGINNER,
            "레그 프레스 머신에서 발판을 밀어내는 하체 복합 운동"),
        exercise("핵 스쿼트 머신", "Hack Squat Machine", MuscleGroup.LEGS, Equipment.MACHINE, Difficulty.INTERMEDIATE,
            "핵 스쿼트 머신에서 수행하는 스쿼트"),
        exercise("레그 익스텐션", "Leg Extension", MuscleGroup.LEGS, Equipment.MACHINE, Difficulty.BEGINNER,
            "머신에 앉아 다리를 펴올려 대퇴사두근을 격리하는 동작"),
        exercise("레그 컬", "Leg Curl", MuscleGroup.LEGS, Equipment.MACHINE, Difficulty.BEGINNER,
            "엎드려 다리를 접어 올려 햄스트링을 격리하는 동작"),
        exercise("카프 레이즈 머신", "Calf Raise Machine", MuscleGroup.LEGS, Equipment.MACHINE, Difficulty.BEGINNER,
            "머신을 이용한 종아리 레이즈"),
        exercise("케이블 풀 스루", "Cable Pull Through", MuscleGroup.LEGS, Equipment.CABLE, Difficulty.BEGINNER,
            "케이블 로우 풀리를 다리 사이로 당겨 둔근과 햄스트링을 자극"),
        exercise("바디웨이트 스쿼트", "Bodyweight Squat", MuscleGroup.LEGS, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "자기 체중만으로 수행하는 기본 스쿼트"),
        exercise("점프 스쿼트", "Jump Squat", MuscleGroup.LEGS, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "스쿼트 후 점프하여 폭발적 하체 파워를 키우는 운동"),
        exercise("밴드 스쿼트", "Band Squat", MuscleGroup.LEGS, Equipment.BAND, Difficulty.BEGINNER,
            "저항 밴드를 무릎 위에 걸고 수행하는 스쿼트"),

        // ──────────────────────────────────────────
        // CORE (14)
        // ──────────────────────────────────────────
        exercise("바벨 롤아웃", "Barbell Rollout", MuscleGroup.CORE, Equipment.BARBELL, Difficulty.ADVANCED,
            "바벨을 잡고 앞으로 굴려 나갔다 돌아오는 코어 운동"),
        exercise("덤벨 사이드 벤드", "Dumbbell Side Bend", MuscleGroup.CORE, Equipment.DUMBBELL, Difficulty.BEGINNER,
            "덤벨을 한 손에 들고 옆으로 기울여 외복사근을 자극"),
        exercise("덤벨 우드촙", "Dumbbell Woodchop", MuscleGroup.CORE, Equipment.DUMBBELL, Difficulty.INTERMEDIATE,
            "덤벨을 대각선으로 들어올리거나 내리며 회전근을 자극"),
        exercise("케이블 크런치", "Cable Crunch", MuscleGroup.CORE, Equipment.CABLE, Difficulty.BEGINNER,
            "하이 풀리 로프를 잡고 무릎 꿇어 수행하는 복근 크런치"),
        exercise("케이블 우드촙", "Cable Woodchop", MuscleGroup.CORE, Equipment.CABLE, Difficulty.INTERMEDIATE,
            "케이블을 사용한 대각선 회전 코어 운동"),
        exercise("케이블 팰로프 프레스", "Cable Pallof Press", MuscleGroup.CORE, Equipment.CABLE, Difficulty.INTERMEDIATE,
            "케이블 저항에 대항하여 팔을 앞으로 뻗는 안티-로테이션 운동"),
        exercise("어브 크런치 머신", "Ab Crunch Machine", MuscleGroup.CORE, Equipment.MACHINE, Difficulty.BEGINNER,
            "머신에 앉아 수행하는 복근 크런치"),
        exercise("플랭크", "Plank", MuscleGroup.CORE, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "엎드려 팔꿈치와 발끝으로 몸을 일직선으로 유지하는 등척성 코어 운동"),
        exercise("사이드 플랭크", "Side Plank", MuscleGroup.CORE, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "옆으로 누워 한 팔꿈치로 버티는 외복사근 운동"),
        exercise("크런치", "Crunch", MuscleGroup.CORE, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "바닥에 누워 상체를 살짝 들어올리는 기본 복근 운동"),
        exercise("레그 레이즈", "Leg Raise", MuscleGroup.CORE, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "바닥이나 매달린 상태에서 다리를 들어올리는 하복부 운동"),
        exercise("행잉 레그 레이즈", "Hanging Leg Raise", MuscleGroup.CORE, Equipment.BODYWEIGHT, Difficulty.ADVANCED,
            "철봉에 매달려 다리를 들어올리는 고급 하복부 운동"),
        exercise("마운틴 클라이머", "Mountain Climber", MuscleGroup.CORE, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "플랭크 자세에서 번갈아 무릎을 당기는 동적 코어 운동"),
        exercise("러시안 트위스트", "Russian Twist", MuscleGroup.CORE, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "앉아서 상체를 좌우로 회전하는 외복사근 운동"),

        // ──────────────────────────────────────────
        // CARDIO (8)
        // ──────────────────────────────────────────
        exercise("트레드밀 런닝", "Treadmill Running", MuscleGroup.CARDIO, Equipment.MACHINE, Difficulty.BEGINNER,
            "트레드밀에서 달리는 유산소 운동"),
        exercise("인클라인 트레드밀 워킹", "Incline Treadmill Walking", MuscleGroup.CARDIO, Equipment.MACHINE, Difficulty.BEGINNER,
            "경사를 올린 트레드밀에서 걷는 유산소 운동"),
        exercise("사이클", "Stationary Bike", MuscleGroup.CARDIO, Equipment.MACHINE, Difficulty.BEGINNER,
            "실내 자전거 유산소 운동"),
        exercise("로잉머신", "Rowing Machine", MuscleGroup.CARDIO, Equipment.MACHINE, Difficulty.INTERMEDIATE,
            "로잉머신을 사용한 전신 유산소 운동"),
        exercise("스텝밀", "Stair Climber", MuscleGroup.CARDIO, Equipment.MACHINE, Difficulty.INTERMEDIATE,
            "계단 오르기 머신 유산소 운동"),
        exercise("일립티컬", "Elliptical", MuscleGroup.CARDIO, Equipment.MACHINE, Difficulty.BEGINNER,
            "일립티컬 머신 유산소 운동"),
        exercise("버피", "Burpee", MuscleGroup.CARDIO, Equipment.BODYWEIGHT, Difficulty.INTERMEDIATE,
            "스쿼트-플랭크-점프를 연속으로 수행하는 전신 유산소 운동"),
        exercise("점핑잭", "Jumping Jack", MuscleGroup.CARDIO, Equipment.BODYWEIGHT, Difficulty.BEGINNER,
            "팔다리를 벌려 뛰는 기본 유산소 운동"),
    )

    private fun exercise(
        name: String,
        nameEn: String,
        muscleGroup: MuscleGroup,
        equipment: Equipment,
        difficulty: Difficulty,
        description: String,
    ) = Exercise(
        name = name,
        nameEn = nameEn,
        muscleGroup = muscleGroup,
        equipment = equipment,
        difficulty = difficulty,
        description = description,
        isCustom = false,
        createdBy = null,
    )
}
