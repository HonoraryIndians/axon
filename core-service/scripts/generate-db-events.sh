#!/bin/bash

##############################################################################
# Direct Database Insert for APPROVED & PURCHASE Events
#
# Simplest approach: Insert directly into campaign_activity_entries table
# to create APPROVED status records.
#
# Prerequisites:
#   - MySQL client installed
#   - Database credentials configured
#
# Usage: ./generate-db-events.sh [activityId] [numApproved]
##############################################################################

set -e

# Configuration
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-axon}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
ACTIVITY_ID="${1:-1}"
NUM_APPROVED="${2:-10}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "💾 Generating APPROVED Events via Direct DB Insert"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Activity ID: $ACTIVITY_ID"
echo "Approved Events: $NUM_APPROVED"
echo "Database: $DB_NAME@$DB_HOST:$DB_PORT"
echo ""

# Build MySQL command
if [ -n "$DB_PASS" ]; then
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p$DB_PASS $DB_NAME"
else
    MYSQL_CMD="mysql -h$DB_HOST -P$DB_PORT -u$DB_USER $DB_NAME"
fi

# Check MySQL connection
echo "🔗 Testing database connection..."
if ! echo "SELECT 1;" | $MYSQL_CMD > /dev/null 2>&1; then
    echo "❌ Error: Cannot connect to database"
    echo ""
    echo "Please check your database credentials:"
    echo "  DB_HOST=$DB_HOST"
    echo "  DB_PORT=$DB_PORT"
    echo "  DB_USER=$DB_USER"
    echo "  DB_NAME=$DB_NAME"
    echo ""
    exit 1
fi
echo "✅ Database connection successful"
echo ""

# Generate SQL for batch insert
echo "✅ Generating $NUM_APPROVED APPROVED entries..."

SQL_VALUES=""
for i in $(seq 1 $NUM_APPROVED); do
    USER_ID=$((4000 + i))
    PRODUCT_ID=$ACTIVITY_ID
    TIMESTAMP=$(date -u +"%Y-%m-%d %H:%M:%S")
    
    if [ $i -gt 1 ]; then
        SQL_VALUES="${SQL_VALUES},"
    fi
    
    SQL_VALUES="${SQL_VALUES}
    (${ACTIVITY_ID}, ${USER_ID}, ${PRODUCT_ID}, 'APPROVED', '${TIMESTAMP}', '${TIMESTAMP}', NULL)"
done

# Execute batch insert
cat <<EOF | $MYSQL_CMD
INSERT INTO campaign_activity_entries 
  (campaign_activity_id, user_id, product_id, status, created_at, updated_at, processed_at)
VALUES ${SQL_VALUES}
ON DUPLICATE KEY UPDATE
  status = 'APPROVED',
  updated_at = NOW();
EOF

INSERTED=$?
if [ $INSERTED -eq 0 ]; then
    echo "✅ Successfully inserted $NUM_APPROVED APPROVED entries"
else
    echo "❌ Error inserting entries"
    exit 1
fi

# Count current entries
echo ""
echo "📊 Current entry counts:"
$MYSQL_CMD -t <<EOF
SELECT 
    status,
    COUNT(*) as count
FROM campaign_activity_entries
WHERE campaign_activity_id = $ACTIVITY_ID
GROUP BY status;
EOF

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Database entries created!"
echo ""
echo "📊 Dashboard should update in ~5 seconds:"
echo "   http://localhost:8080/admin/dashboard/${ACTIVITY_ID}"
echo ""
echo "💡 Note: PURCHASE events are triggered when entries have APPROVED status"
echo "   and purchase-related activity type. Check your activity configuration."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
