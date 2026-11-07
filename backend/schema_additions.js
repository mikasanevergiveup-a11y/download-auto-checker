// Add these tables into tgcloud/schema.js

export const androidCodes = table('android_codes', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  telegramId: integer('telegram_id').notNull(),
  code: text('code').notNull(),
  expiresAt: integer('expires_at').notNull(),
  used: boolean('used').notNull().default(false),
  createdAt: integer('created_at', { mode: 'timestamp' }).default(sql`(unixepoch())`),
});

export const androidSessions = table('android_sessions', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  telegramId: integer('telegram_id').notNull(),
  token: text('token').notNull().unique(),
  deviceId: text('device_id').notNull(),
  createdAt: integer('created_at', { mode: 'timestamp' }).default(sql`(unixepoch())`),
  lastSeenAt: integer('last_seen_at').notNull().default(0),
});

export const androidClaims = table('android_claims', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  telegramId: integer('telegram_id').notNull(),
  taskType: integer('task_type').notNull(),
  idempotencyKey: text('idempotency_key').notNull().unique(),
  packagesJson: text('packages_json'),
  rewardAmount: integer('reward_amount').notNull().default(0),
  rewardType: integer('reward_type').notNull().default(1),
  createdAt: integer('created_at', { mode: 'timestamp' }).default(sql`(unixepoch())`),
});
