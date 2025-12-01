-- ============================================
-- 마케팅 지표 테스트를 위한 데이터 셋업
-- ============================================
-- 사용법:
-- docker exec -i axon-mysql mysql -u axon_user -paxon_password axon_db < core-service/scripts/setup-marketing-test-data.sql

-- Step 1: 제품 생성 (아이폰 16 Pro)
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM products WHERE id = 1;
INSERT INTO products (id, product_name, price, stock)
VALUES (1, 'iPhone 16 Pro 256GB', 1590000.00, 1000);

SET @product_id = 1;
SELECT CONCAT('✅ 제품 생성 완료 - ID: ', @product_id, ', 가격: ₩1,590,000') AS result;

-- Step 2: 캠페인 생성
DELETE FROM campaigns WHERE id = 1;
INSERT INTO campaigns (id, name, start_at, end_at, created_at, updated_at)
VALUES (
    1,
    '블랙프라이데이 2024',
    NOW() - INTERVAL 7 DAY,
    NOW() + INTERVAL 30 DAY,
    NOW(),
    NOW()
);

SET @campaign_id = 1;
SELECT CONCAT('✅ 캠페인 생성 완료 - ID: ', @campaign_id) AS result;

-- Step 3: 선착순 액티비티 생성 (핵심!)
-- 가격: 1,290,000원 (정가 대비 30만원 할인)
-- 수량: 100개 한정
-- 예산: 5,000,000원 (마케팅 비용)
DELETE FROM campaign_activities WHERE id = 1;
INSERT INTO campaign_activities (
    id,
    campaign_id,
    product_id,
    name,
    activity_type,
    status,
    start_date,
    end_date,
    price,
    quantity,
    limit_count,
    budget,
    created_at,
    updated_at
) VALUES (
    1,
    @campaign_id,
    @product_id,
    '아이폰 16 Pro 선착순 특가',
    'FIRST_COME_FIRST_SERVE',
    'ACTIVE',
    NOW() - INTERVAL 7 DAY,
    NOW() + INTERVAL 30 DAY,
    1290000.00,        -- 할인가격
    100,               -- 재고 수량
    100,               -- 참여 제한 (선착순 100명)
    5000000.00,        -- 마케팅 예산 (500만원)
    NOW(),
    NOW()
);

SET @activity_id = 1;

-- Step 4: 생성된 데이터 확인
SELECT
    CONCAT('✅ 액티비티 생성 완료 - ID: ', @activity_id) AS result,
    CONCAT('   이름: 아이폰 16 Pro 선착순 특가') AS name,
    CONCAT('   할인가: ₩', FORMAT(1290000, 0)) AS price,
    CONCAT('   수량: 100개') AS quantity,
    CONCAT('   예산: ₩', FORMAT(5000000, 0)) AS budget,
    CONCAT('   예상 GMV (전량 판매시): ₩', FORMAT(1290000 * 100, 0)) AS max_gmv,
    CONCAT('   예상 ROAS (전량 판매시): ', FORMAT((1290000 * 100 / 5000000) * 100, 1), '%') AS max_roas;

-- Step 5: 테스트용 사용자 생성 (스크립트에서 사용할 userId)
-- userId 1000~1200 범위 생성
-- Procedure to generate users
DROP PROCEDURE IF EXISTS GenerateUsers;
DELIMITER //
CREATE PROCEDURE GenerateUsers()
BEGIN
    DECLARE counter INT DEFAULT 1000;
    WHILE counter <= 1200 DO
        INSERT IGNORE INTO users (id, email, name, role, grade, created_at, updated_at)
        VALUES (
            counter,
            CONCAT('test', counter, '@axon.com'),
            CONCAT('테스트유저', counter),
            'USER',
            'BRONZE',
            NOW(),
            NOW()
        );

        -- Create corresponding user_summary record
        INSERT IGNORE INTO user_summary (user_id, last_login_at, last_purchase_at)
        VALUES (counter, NULL, NULL);

        SET counter = counter + 1;
    END WHILE;
END //
DELIMITER ;

CALL GenerateUsers();
DROP PROCEDURE GenerateUsers;

-- Step 6: 초기 구매 데이터 생성 (Cohort 형성)
-- 1000~1100번 유저가 7일 전에 구매했다고 가정
DROP PROCEDURE IF EXISTS GenerateInitialPurchases;
DELIMITER //
CREATE PROCEDURE GenerateInitialPurchases(IN activityId BIGINT, IN productId BIGINT)
BEGIN
    DECLARE counter INT DEFAULT 1000;
    WHILE counter <= 1100 DO
        INSERT INTO purchases (
            user_id, product_id, campaign_activity_id, purchase_type, 
            price, quantity, purchase_at
        ) VALUES (
            counter,
            productId,
            activityId,
            'CAMPAIGNACTIVITY',
            1290000.00,
            1,
            NOW() - INTERVAL 7 DAY
        );
        SET counter = counter + 1;
    END WHILE;
END //
DELIMITER ;

-- CALL GenerateInitialPurchases(@activity_id, @product_id);
-- DROP PROCEDURE GenerateInitialPurchases;

-- SELECT '✅ 초기 구매 데이터 101건 생성 완료 (Cohort 형성)' AS result;

SELECT '✅ 테스트 유저 201명 생성 완료 (ID: 1000~1200)' AS result;

-- 최종 요약
SELECT '========================================' AS '';
SELECT '🎉 마케팅 테스트 데이터 셋업 완료!' AS '';
SELECT '========================================' AS '';
SELECT CONCAT('Campaign ID: ', @campaign_id) AS info;
SELECT CONCAT('Activity ID: ', @activity_id) AS info;
SELECT CONCAT('Product ID: ', @product_id) AS info;
SELECT '' AS '';
SELECT '📊 예상 시나리오 (구매 50건 발생시):' AS '';
SELECT '   - GMV: ₩64,500,000' AS metric;
SELECT '   - 평균 객단가: ₩1,290,000' AS metric;
SELECT '   - ROAS: 1,290%' AS metric;
SELECT '   - 전환율: 방문자 수 대비 계산' AS metric;
SELECT '' AS '';
SELECT '▶️ 다음 단계: bash core-service/scripts/generate-full-funnel.sh' AS next_step;

SET FOREIGN_KEY_CHECKS = 1;
