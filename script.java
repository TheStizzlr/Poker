// Use pokersolver
const Hand = window.Hand;

// ----- Deck / Card Logic -----
class Deck {
  constructor() {
    this.reset();
    this.shuffle();
  }

  reset() {
    this.cards = [];
    const suits = ['s', 'h', 'd', 'c'];
    const values = ['2','3','4','5','6','7','8','9','T','J','Q','K','A'];
    for (const s of suits) {
      for (const v of values) {
        this.cards.push({ value: v, suit: s });
      }
    }
  }

  shuffle() {
    for (let i = this.cards.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [this.cards[i], this.cards[j]] = [this.cards[j], this.cards[i]];
    }
  }

  dealOne() {
    return this.cards.pop();
  }
}

function renderCard(card) {
  const div = document.createElement('div');
  div.classList.add('card');
  const suitSymbols = { s: '♠', h: '♥', d: '♦', c: '♣' };
  if (card.suit === 'h' || card.suit === 'd') div.classList.add('red');
  div.textContent = `${card.value}${suitSymbols[card.suit]}`;
  return div;
}

// ----- Game State -----
let deck;
let board = [];
let pot = 0;
let currentBet = 0; // current bet to call
let players = [];
let currentPlayerIndex = 0;
let currentRound = 0; // 0: pre-flop, 1: flop, 2: turn, 3: river, 4: showdown
const rounds = ['Pre-flop', 'Flop', 'Turn', 'River', 'Showdown'];

// ----- UI Elements -----
const playersArea = document.getElementById('playersArea');
const communityCardsEl = document.getElementById('communityCards');
const dealButton = document.getElementById('dealButton');
const actionButton = document.getElementById('actionButton');
const foldButton = document.getElementById('foldButton');
const raiseButton = document.getElementById('raiseButton');
const potDisplay = document.getElementById('potDisplay');
const logEl = document.getElementById('log');

function log(msg) {
  const p = document.createElement('p');
  p.textContent = msg;
  logEl.append(p);
  logEl.scrollTop = logEl.scrollHeight;
}

// ----- Player / Bot Setup -----
function setupPlayers(num = 3) {
  players = [];
  playersArea.innerHTML = '';
  for (let i = 0; i < num; i++) {
    const player = {
      name: i === 0 ? 'You' : `Bot ${i}`,
      hole: [],
      folded: false,
      allIn: false,
      chips: 100,
      currentBet: 0,
      el: null,
      statusEl: null,
      chipsEl: null
    };
    const container = document.createElement('div');
    container.classList.add('player-hand');
    container.innerHTML = `
      <h3>${player.name}</h3>
      <div class="cards" id="player${i}Cards"></div>
      <div class="status" id="player${i}Status"></div>
      <div class="chips" id="player${i}Chips">Chips: $${player.chips}</div>
    `;
    playersArea.append(container);
    player.el = container.querySelector(`#player${i}Cards`);
    player.statusEl = container.querySelector(`#player${i}Status`);
    player.chipsEl = container.querySelector(`#player${i}Chips`);
    players.push(player);
  }
}

// ----- Reset for a New Hand -----
function resetGame() {
  deck = new Deck();
  board = [];
  pot = 0;
  currentBet = 0;
  currentRound = 0;
  currentPlayerIndex = 0;
  communityCardsEl.innerHTML = '';
  logEl.innerHTML = '';
  players.forEach(p => {
    p.hole = [];
    p.folded = false;
    p.allIn = false;
    p.currentBet = 0;
    p.statusEl.textContent = '';
    p.el.innerHTML = '';
    p.chipsEl.textContent = `Chips: $${p.chips}`;
  });
  potDisplay.textContent = `Pot: $${pot}`;
}

// ----- Deal Hole Cards -----
function dealInitial() {
  resetGame();
  deck.shuffle();
  players.forEach(p => {
    p.hole = [deck.dealOne(), deck.dealOne()];
    p.el.append(renderCard(p.hole[0]));
    p.el.append(renderCard(p.hole[1]));
    log(`${p.name} is dealt ${p.hole[0].value}${p.hole[0].suit}, ${p.hole[1].value}${p.hole[1].suit}`);
  });
  log(`Round: ${rounds[currentRound]}`);
  enablePlayerAction();
}

// ----- Deal Community Cards -----
function dealBoardCards() {
  if (currentRound === 1) {
    // flop
    for (let i = 0; i < 3; i++) {
      const c = deck.dealOne();
      board.push(c);
      communityCardsEl.append(renderCard(c));
    }
    log(`Flop: ${board.map(c => c.value + c.suit).join(', ')}`);
  } else if (currentRound === 2) {
    const c = deck.dealOne();
    board.push(c);
    communityCardsEl.append(renderCard(c));
    log(`Turn: ${c.value}${c.suit}`);
  } else if (currentRound === 3) {
    const c = deck.dealOne();
    board.push(c);
    communityCardsEl.append(renderCard(c));
    log(`River: ${c.value}${c.suit}`);
  }
}

// ----- Betting / Action Flow -----
function enablePlayerAction() {
  // Only enable if it's a real player's turn, not after showdown
  if (currentRound < 4) {
    actionButton.disabled = false;
    foldButton.disabled = false;
    raiseButton.disabled = false;
  }
}

function disablePlayerAction() {
  actionButton.disabled = true;
  foldButton.disabled = true;
  raiseButton.disabled = true;
}

function nextPlayer() {
  // Move to next active (not folded / all-in) player
  let tries = 0;
  do {
    currentPlayerIndex = (currentPlayerIndex + 1) % players.length;
    tries++;
    if (tries > players.length) break;
  } while (players[currentPlayerIndex].folded || players[currentPlayerIndex].allIn);
}

function allPlayersActed() {
  // Simplistic: check if every active player has matched the currentBet
  return players.every(p => p.folded || p.allIn || p.currentBet === currentBet);
}

function startBettingRound() {
  // Reset their currentBet for this round
  players.forEach(p => p.currentBet = 0);
  currentBet = 0;
  currentPlayerIndex = 0;
  enablePlayerAction();
}

// Called when "Action" (call) by the user
function onCall() {
  const player = players[currentPlayerIndex];
  const toCall = currentBet - player.currentBet;
  const amount = Math.min(toCall, player.chips);
  player.chips -= amount;
  player.currentBet += amount;
  pot += amount;
  log(`${player.name} calls $${amount}.`);
  if (player.chips === 0) {
    player.allIn = true;
    log(`${player.name} is all-in!`);
  }
  player.chipsEl.textContent = `Chips: $${player.chips}`;
  potDisplay.textContent = `Pot: $${pot}`;

  if (allPlayersActed()) {
    // move to next round
    disablePlayerAction();
    nextRound();
  } else {
    nextPlayer();
    if (players[currentPlayerIndex].name.startsWith("Bot")) {
      botAction();
    } else {
      enablePlayerAction();
    }
  }
}

// Called when user "Raise"
function onRaise() {
  const player = players[currentPlayerIndex];
  const raiseAmt = 10; // fixed raise for simplicity, but you can make dynamic
  const total = (currentBet - player.currentBet) + raiseAmt;
  const toPut = Math.min(total, player.chips);
  player.chips -= toPut;
  player.currentBet += toPut;
  pot += toPut;
  currentBet = player.currentBet; // new current bet to call
  log(`${player.name} raises by $${raiseAmt}, total bet $${player.currentBet}.`);
  if (player.chips === 0) {
    player.allIn = true;
    log(`${player.name} is all-in on raise!`);
  }
  player.chipsEl.textContent = `Chips: $${player.chips}`;
  potDisplay.textContent = `Pot: $${pot}`;

  // After raise, reset other players’ “acted” logic so they have to call this bet
  // For simplicity, we’ll just go to next player
  nextPlayer();
  if (players[currentPlayerIndex].name.startsWith("Bot")) {
    botAction();
  } else {
    enablePlayerAction();
  }
}

// When user folds
function onFold() {
  const player = players[currentPlayerIndex];
  player.folded = true;
  log(`${player.name} folds.`);
  player.statusEl.textContent = "Folded";
  disablePlayerAction();
  
  // Check if only one remains
  const active = players.filter(p => !p.folded && !p.allIn);
  if (active.length === 0) {
    // showdown
    nextRound();
  } else {
    nextPlayer();
    if (players[currentPlayerIndex].name.startsWith("Bot")) {
      botAction();
    } else {
      enablePlayerAction();
    }
  }
}

// Simple bot logic — VERY basic strategy
function botAction() {
  const bot = players[currentPlayerIndex];
  // If bet to call is zero, sometimes check / raise
  const toCall = currentBet - bot.currentBet;
  const handStrength = evaluateHandStrength(bot); // custom heuristic
  // Simple thresholds
  if (toCall === 0) {
    if (handStrength > 0.7 && bot.chips > 5) {
      onRaise();
    } else {
      onCall();
    }
  } else {
    // There's a bet — decide to call or fold
    if (handStrength > 0.5 && toCall < bot.chips * 0.5) {
      onCall();
    } else {
      // fold
      bot.folded = true;
      log(`${bot.name} folds.`);
      bot.statusEl.textContent = "Folded";
      disablePlayerAction();
      nextPlayer();
      if (allPlayersActed()) {
        nextRound();
      } else {
        if (players[currentPlayerIndex].name.startsWith("Bot")) {
          botAction();
        } else {
          enablePlayerAction();
        }
      }
    }
  }
}

// Very simple heuristic for hand strength (pre-flop or post) — you can improve this
function evaluateHandStrength(player) {
  const cards = player.hole.concat(board);
  const cardStrings = cards.map(c => c.value + c.suit);
  const hand = Hand.solve(cardStrings);
  // higher-rank hands -> lower rank number is better in solver, so invert
  // But Hand.rank is not exposed, so approximate: we could check name / descr
  const name = hand.name.toLowerCase();
  let strength = 0.5;
  if (name.includes('pair') || name.includes('two pair')) strength = 0.7;
  if (name.includes('trip') || name.includes('straight') || name.includes('flush')) strength = 0.85;
  if (name.includes('full house') || name.includes('quads') || name.includes('straight flush')) strength = 0.95;
  return strength;
}

// ----- Advance Round / Showdown -----
function nextRound() {
  currentRound++;
  if (currentRound <= 3) {
    dealBoardCards();
    log(`Round: ${rounds[currentRound]}`);
    startBettingRound();
    if (players[currentPlayerIndex].name.startsWith("Bot")) {
      botAction();
    } else {
      enablePlayerAction();
    }
  } else {
    // Showdown
    log(`Round: ${rounds[4]}`);
    showdown();
    disablePlayerAction();
  }
}

// Showdown logic using pokersolver
function showdown() {
  const solved = [];
  players.forEach(p => {
    if (!p.folded) {
      const cardStr = p.hole.concat(board).map(c => c.value + c.suit);
      const hand = Hand.solve(cardStr);
      solved.push({ player: p, hand });
      log(`${p.name}: ${hand.name} (${hand.descr})`);
    }
  });
  const hands = solved.map(o => o.hand);
  const winners = Hand.winners(hands);
  const winningPlayers = solved
    .filter(o => winners.includes(o.hand))
    .map(o => o.player);

  if (winningPlayers.length === 1) {
    const w = winningPlayers[0];
    log(`${w.name} wins the pot of $${pot}!`);
    w.statusEl.textContent = 'Winner!';
    w.chips += pot;  // winner takes pot
  } else {
    const names = winningPlayers.map(p => p.name).join(', ');
    log(`Tie between ${names}. Pot is split.`);
    const split = Math.floor(pot / winningPlayers.length);
    winningPlayers.forEach(p => {
      p.chips += split;
      p.statusEl.textContent = 'Tie';
    });
  }

  // Update chips UI
  players.forEach(p => {
    p.chipsEl.textContent = `Chips: $${p.chips}`;
  });
}

// ----- Event Listeners -----
dealButton.addEventListener('click', () => {
  setupPlayers(3); // you can change number of players
  dealInitial();
});

actionButton.addEventListener('click', onCall);
raiseButton.addEventListener('click', onRaise);
foldButton.addEventListener('click', onFold);
