-- ============================================
-- MySQL Cleanup Commands for Dashboard Tests
-- This script is designed to be executed via `mysql -e` or `mysql < file.sql`
-- Placeholders for ACTIVITY_ID_PLACEHOLDER and TEST_USER_ID_MIN will be replaced by the calling shell script.
-- ============================================

SET FOREIGN_KEY_CHECKS = 0;

-- Delete purchases first as they might reference campaign_activity_entries or users
DELETE FROM purchases WHERE campaign_activity_id = ACTIVITY_ID_PLACEHOLDER;
-- Delete repurchases (SHOP type) by users who participated in this activity
-- This assumes repurchases have NULL campaign_activity_id and are linked by user_id
DELETE FROM purchases WHERE campaign_activity_id IS NULL AND user_id >= TEST_USER_ID_MIN;

-- Delete campaign activity entries
DELETE FROM campaign_activity_entries WHERE campaign_activity_id = ACTIVITY_ID_PLACEHOLDER;

-- Delete user summaries for test users
DELETE FROM user_summary WHERE user_id >= TEST_USER_ID_MIN;

-- Delete test users
DELETE FROM users WHERE id >= TEST_USER_ID_MIN;

-- Delete the specific campaign activity
DELETE FROM campaign_activities WHERE id = ACTIVITY_ID_PLACEHOLDER;

-- Delete the campaign if it was created specifically for this activity
DELETE FROM campaigns WHERE id = CAMPAIGN_ID_PLACEHOLDER;

-- Delete products if they were created specifically for this activity
DELETE FROM products WHERE id = PRODUCT_ID_PLACEHOLDER;

SET FOREIGN_KEY_CHECKS = 1;