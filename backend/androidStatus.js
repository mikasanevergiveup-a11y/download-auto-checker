import { db } from 'sdk';
import { EndpointError } from 'sdk';
import { eq } from 'sdk/db';
import { users, androidSessions, androidClaims } from '../schema.js';
import { getSetting } from '../lib/db.js';

export default async function (input) {
  const auth = String(input?.sessionToken || '').trim();
  if (!auth) throw new EndpointError('Unauthorized', { code: 'NO_TOKEN' });
  const sessions = await db.select().from(androidSessions).where(eq(androidSessions.token, auth)).all();
  const session = sessions[0];
  if (!session) throw new EndpointError('Invalid session', { code: 'SESSION' });

  const user = await db.select().from(users).where(eq(users.telegramId, session.telegramId)).get();
  const claims = await db.select().from(androidClaims).where(eq(androidClaims.telegramId, session.telegramId)).all();
  const task1Done = claims.some((c) => c.taskType === 1);
  const task2Done = claims.some((c) => c.taskType === 2);
  let stage = 'TASK1';
  if (task1Done && !task2Done) stage = 'TASK2';
  if (task1Done && task2Done) stage = 'COMPLETED';

  return {
    ok: true,
    stage,
    task1Done,
    task2Done,
    requiredInstalls: 5,
    rewardTask1: parseInt(await getSetting('reward_task1') || '100', 10),
    rewardTask2: parseInt(await getSetting('reward_task2') || '150', 10),
    user: user ? {
      telegramId: user.telegramId,
      username: user.username,
      coin: user.coin,
      dcoin: user.dcoin,
      mmk: user.mmk,
      premiumUntil: user.premiumUntil,
      premiumActive: (user.premiumUntil || 0) > Math.floor(Date.now() / 1000),
    } : null,
  };
}
