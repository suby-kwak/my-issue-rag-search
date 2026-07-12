require('dotenv').config();
const { Client, GatewayIntentBits, Partials } = require('discord.js');

const DISCORD_BOT_TOKEN = process.env.DISCORD_BOT_TOKEN;
const RAG_API_URL = process.env.RAG_API_URL || 'http://localhost:8080/api/query';

if (!DISCORD_BOT_TOKEN) {
  console.error('DISCORD_BOT_TOKEN is not set. Copy .env.example to .env and fill it in.');
  process.exit(1);
}

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
    GatewayIntentBits.DirectMessages,
  ],
  partials: [Partials.Channel],
});

function extractQuestion(message) {
  if (message.guild) {
    return message.content.replace(`<@${client.user.id}>`, '').replace(`<@!${client.user.id}>`, '').trim();
  }
  return message.content.trim();
}

function formatReply(answer, sources) {
  let reply = answer;
  if (sources && sources.length > 0) {
    const sourceLines = sources.map((s) => `- [${s.title}](${s.url})`).join('\n');
    reply += `\n\n**출처**\n${sourceLines}`;
  }
  return reply.length > 1900 ? `${reply.slice(0, 1900)}...` : reply;
}

client.on('messageCreate', async (message) => {
  if (message.author.bot) return;

  const isDm = !message.guild;
  const isMentioned = message.guild && message.mentions.has(client.user);
  if (!isDm && !isMentioned) return;

  const question = extractQuestion(message);
  if (!question) return;

  await message.channel.sendTyping();

  try {
    const response = await fetch(RAG_API_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=UTF-8' },
      body: JSON.stringify({ question }),
    });

    if (!response.ok) {
      const errorBody = await response.text();
      console.error(`RAG API error ${response.status}:`, errorBody);
      await message.reply('질문 처리 중 오류가 발생했습니다.');
      return;
    }

    const { answer, sources } = await response.json();
    await message.reply(formatReply(answer, sources));
  } catch (err) {
    console.error('Failed to reach RAG API:', err);
    await message.reply('RAG 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.');
  }
});

client.once('clientReady', () => {
  console.log(`Discord bot ready as ${client.user.tag}`);
});

client.login(DISCORD_BOT_TOKEN);
