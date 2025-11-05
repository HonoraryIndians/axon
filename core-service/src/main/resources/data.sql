INSERT INTO products (id, product_name, stock)
VALUES (1, '테스트 상품 A', 100)
    ON DUPLICATE KEY UPDATE product_name = VALUES(product_name), stock = VALUES(stock);

INSERT INTO campaigns (id, name, start_at, end_at, target_segment_id, reward_type, reward_payload, created_at, updated_at)
VALUES (1, '선착순 캠페인', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 7 DAY, NULL, 'COUPON', '{"code":"WELCOME10"}', NOW(), NOW())
    ON DUPLICATE KEY UPDATE name = VALUES(name), reward_payload = VALUES(reward_payload), updated_at = NOW();

INSERT INTO campaign_activities (id, campaign_id, name, limit_count, status, start_date, end_date, activity_type, created_at, updated_at)
VALUES (1, 1, '선착순 테스트', 100, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 7 DAY, 'FIRST_COME_FIRST_SERVE', NOW(), NOW())
    ON DUPLICATE KEY UPDATE name = VALUES(name), limit_count = VALUES(limit_count), end_date = VALUES(end_date), updated_at = NOW();

INSERT INTO events (created_at, id, updated_at, description, name, trigger_payload, status,trigger_type)
VALUES (NOW(), 1, NOW(), '구매호ㅓㅏㄱ정', '구매','{}', 'ACTIVE', 'PURCHASE');