-- ============================================
-- 캠페인 대시보드 테스트를 위한 멀티 Activity 데이터 셋업
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 기존 데이터 정리
DELETE FROM campaign_activities WHERE id IN (1, 2);
DELETE FROM campaigns WHERE id = 1;
DELETE FROM products WHERE id IN (1, 2);

-- 2. 제품 생성
INSERT INTO products (id, product_name, price, stock) VALUES (1, 'iPhone 16 Pro', 1500000, 1000);
INSERT INTO products (id, product_name, price, stock) VALUES (2, 'MacBook Pro M3', 2500000, 500);

-- 3. 캠페인 생성
INSERT INTO campaigns (id, name, start_at, end_at, created_at, updated_at, budget)
VALUES (1, '연말 감사제 Big Sale', NOW() - INTERVAL 7 DAY, NOW() + INTERVAL 7 DAY, NOW(), NOW(), 10000000);

-- 4. Activity 1 생성 (아이폰)
INSERT INTO campaign_activities (
    id, campaign_id, product_id, name, activity_type, status, 
    start_date, end_date, price, quantity, limit_count, budget, created_at, updated_at
) VALUES (
    1, 1, 1, '아이폰 선착순 특가', 'FIRST_COME_FIRST_SERVE', 'ACTIVE',
    NOW() - INTERVAL 7 DAY, NOW() + INTERVAL 7 DAY, 
    1300000, 100, 100, 5000000, NOW(), NOW()
);

-- 5. Activity 2 생성 (맥북)
INSERT INTO campaign_activities (
    id, campaign_id, product_id, name, activity_type, status, 
    start_date, end_date, price, quantity, limit_count, budget, created_at, updated_at
) VALUES (
    2, 1, 2, '맥북 프로 한정 할인', 'FIRST_COME_FIRST_SERVE', 'ACTIVE',
    NOW() - INTERVAL 7 DAY, NOW() + INTERVAL 7 DAY, 
    2200000, 50, 50, 5000000, NOW(), NOW()
);

-- 6. 테스트 유저 생성 (1000~1200)
-- (기존 프로시저 활용 가능하면 생략, 없으면 재생성)
DROP PROCEDURE IF EXISTS GenerateUsers;
DELIMITER //
CREATE PROCEDURE GenerateUsers()
BEGIN
    DECLARE counter INT DEFAULT 1000;
    WHILE counter <= 1200 DO
        INSERT IGNORE INTO users (id, email, name, role, grade, created_at, updated_at)
        VALUES (counter, CONCAT('test', counter, '@axon.com'), CONCAT('User', counter), 'USER', 'BRONZE', NOW(), NOW());
        INSERT IGNORE INTO user_summary (user_id) VALUES (counter);
        SET counter = counter + 1;
    END WHILE;
END //
DELIMITER ;
CALL GenerateUsers();
DROP PROCEDURE GenerateUsers;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '✅ Campaign(1) with 2 Activities(1, 2) Created!' AS result;
