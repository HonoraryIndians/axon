SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM products WHERE id = PRODUCT_ID_PLACEHOLDER;
INSERT INTO products (id, product_name, price, stock) VALUES (PRODUCT_ID_PLACEHOLDER, 'iPhone 16 Pro 256GB', 1590000.00, 1000);

DELETE FROM campaigns WHERE id = CAMPAIGN_ID_PLACEHOLDER;
INSERT INTO campaigns (id, name, start_at, end_at, created_at, updated_at) VALUES (CAMPAIGN_ID_PLACEHOLDER, '블랙프라이데이 2024', NOW() - INTERVAL 7 DAY, NOW() + INTERVAL 30 DAY, NOW(), NOW());

DELETE FROM campaign_activities WHERE id = ACTIVITY_ID_PLACEHOLDER;
INSERT INTO campaign_activities (id, campaign_id, product_id, name, activity_type, status, start_date, end_date, price, quantity, limit_count, budget, created_at, updated_at) VALUES (ACTIVITY_ID_PLACEHOLDER, CAMPAIGN_ID_PLACEHOLDER, PRODUCT_ID_PLACEHOLDER, '아이폰 16 Pro 선착순 특가', 'FIRST_COME_FIRST_SERVE', 'ACTIVE', NOW() - INTERVAL 7 DAY, NOW() + INTERVAL 30 DAY, 1290000.00, 100, 100, 5000000.00, NOW(), NOW());

DROP PROCEDURE IF EXISTS GenerateUsers;
DELIMITER //
CREATE PROCEDURE GenerateUsers()
BEGIN
    DECLARE counter INT DEFAULT TEST_USER_ID_MIN;
    WHILE counter <= (TEST_USER_ID_MIN + 200) DO
        INSERT IGNORE INTO users (id, email, name, role, grade, created_at, updated_at) VALUES (counter, CONCAT('test', counter, '@axon.com'), CONCAT('테스트유저', counter), 'USER', 'BRONZE', NOW(), NOW());
        INSERT IGNORE INTO user_summary (user_id, last_login_at, last_purchase_at) VALUES (counter, NULL, NULL);
        SET counter = counter + 1;
    END WHILE;
END //
DELIMITER ;
CALL GenerateUsers();
DROP PROCEDURE GenerateUsers;

DROP PROCEDURE IF EXISTS GenerateInitialPurchases;
DELIMITER //
CREATE PROCEDURE GenerateInitialPurchases(IN activityId BIGINT, IN productId BIGINT, IN minUserId BIGINT, IN maxUserId BIGINT)
BEGIN
    DECLARE counter BIGINT DEFAULT minUserId;
    WHILE counter <= maxUserId DO
        INSERT INTO purchases (user_id, product_id, campaign_activity_id, purchase_type, price, quantity, purchase_at) VALUES (counter, productId, activityId, 'CAMPAIGNACTIVITY', 1290000.00, 1, NOW() - INTERVAL 7 DAY);
        SET counter = counter + 1;
    END WHILE;
END //
DELIMITER ;

SET FOREIGN_KEY_CHECKS = 1;
