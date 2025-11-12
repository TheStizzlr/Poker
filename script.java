// Uses the ‘pokersolver’ library for hand evaluation: https://github.com/goldfire/pokersolver 

class Deck {
  constructor() {
    this.reset();
    this.shuffle();
  }
  reset() {
    this.cards = [];
    const suits = ['s','h','d','c'];
    const values = ['2','3','4','5','6','7','8','9','T','J','Q','K','A'];
    for (let s of suits) {
      for (let v of values) {
        this.cards.push({ value: v, suit: s });
      }
    }
  }
  shuffle() {
    const { cards } = this;
    for (let i = cards.length-1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i+1));
      [cards[i], cards[j]] = [cards[j], cards[i]];
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
  div.textContent = `${card.value}${suitSymbols[card.suit]}`;
  return div;
}

// Game variables
let deck;
let board = [];
let pot = 0;
let players = [];
let currentRound = 0; // 0=pre-flop,1=flop,2=turn,3=river,4=showdown
const rounds = ['Pre-flop','Flop','Turn','River','Showdown'];

const playersArea = document.getElementById('playersArea');
const communityCardsEl = document.getElementById('communityCards');
const dealButton = document.getElementById('dealButton');
const betButton = document.getElementById('betButton');
const foldButton = document.getElementById('foldButton');
const potDisplay = document.getElementById('potDisplay');
const logEl = document.getElementById('log');

function log(msg) {
  const p = document.createElement('p');
  p.textContent = msg;
  logEl.append(p);
  logEl.scrollTop = logEl.scrollHeight;
}

function setupPlayers(num = 3) {
  players = [];
  playersArea.innerHTML = '';
  for (let i = 1; i <= num; i++) {
    const p = {
      name: (i === 1 ? 'You' : `Player ${i}`),
      hole: [],
      folded: false,
      chips: 100,  // start chips
      el: null,
      statusEl: null
    };
    const container = document.createElement('div');
    container.classList.add('player-hand');
    container.innerHTML = `<h3>${p.name}</h3><div class="cards" id="player${i}Cards"></div><div class="status" id="player${i}Status"></div><div class="chips" id="player${i}Chips">Chips: $${p.chips}</div>`;
    playersArea.append(container);
    p.el = container.querySelector(`#player${i}Cards`);
    p.statusEl = container.querySelector(`#player${i}Status`);
    players.push(p);
  }
}

function resetGame() {
  deck = new Deck();
  board = [];
  pot = 0;
  currentRound = 0;
  communityCardsEl.innerHTML = '';
  players.forEach(p => {
    p.hole = [];
    p.folded = false;
    p.statusEl.textContent = '';
    p.el.innerHTML = '';
  });
  potDisplay.textContent = `Pot: $${pot}`;
  logEl.innerHTML = '';
}

function dealInitial() {
  resetGame();
  setupPlayers(players.length);
  deck.shuffle();
  // Deal two hole cards each
  players.forEach(p => {
    p.hole.push(deck.dealOne());
    p.hole.push(deck.dealOne());
    p.el.append(renderCard(p.hole[0]));
    p.el.append(renderCard(p.hole[1]));
    log(`${p.name} was dealt two hole cards.`);
  });
  log(`Round: ${rounds[currentRound]}`);
  betButton.disabled = false;
  foldButton.disabled = false;
}

function dealBoardCards() {
  if (currentRound === 1) {
    // flop: 3 cards
    for (let i = 0; i < 3; i++) {
      const c = deck.dealOne();
      board.push(c);
      communityCardsEl.append(renderCard(c));
    }
    log(`Flop dealt: ${board.map(c=>c.value+c.suit).join(', ')}`);
  } else if (currentRound === 2) {
    const c = deck.dealOne();
    board.push(c);
    communityCardsEl.append(renderCard(c));
    log(`Turn dealt.`);
  } else if (currentRound === 3) {
    const c = deck.dealOne();
    board.push(c);
    communityCardsEl.append(renderCard(c));
    log(`River dealt.`);
  }
}

function nextRound() {
  if (currentRound < 3) {
    currentRound++;
    dealBoardCards();
    log(`Round: ${rounds[currentRound]}`);
  } else {
    currentRound = 4;
    log(`Round: ${rounds[currentRound]}`);
    showdown();
    betButton.disabled = true;
    foldButton.disabled = true;
  }
  potDisplay.textContent = `Pot: $${pot}`;
}

function playerFold(playerIndex) {
  const p = players[playerIndex];
  if (p.folded) return;
  p.folded = true;
  p.statusEl.textContent = 'Folded';
  log(`${p.name} folds.`);
  const active = players.filter(pl => !pl.folded);
  if (active.length === 1) {
    log(`${active[0].name} wins the pot of $${pot} by default.`);
    betButton.disabled = true;
    foldButton.disabled = true;
  }
}

function showdown() {
  const { Hand } = window.PokerSolver;  // from pokersolver library
  const hands = [];
  players.forEach(p => {
    if (!p.folded) {
      const cardsAsStrings = p.hole.concat(board).map(c => c.value + c.suit);
      const hand = Hand.solve(cardsAsStrings);
      hands.push({ player: p, hand });
      log(`${p.name} has ‟${hand.name}” (${hand.descr}).`);
    }
  });
  const winners = Hand.winners(hands.map(h => h.hand));
  // winners is an array of winning hand objects
  const winningPlayers = hands.filter(h => winners.includes(h.hand)).map(h => h.player);
  if (winningPlayers.length === 1) {
    log(`${winningPlayers[0].name} wins the pot of $${pot}!`);
  } else {
    const names = winningPlayers.map(p => p.name).join(', ');
    log(`Tie between ${names}. They split the pot of $${pot}.`);
  }
}

dealButton.addEventListener('click', () => {
  setupPlayers(3); // you can change number of players here
  dealInitial();
});

betButton.addEventListener('click', () => {
  const betAmount = 10;
  const active = players.filter(p => !p.folded);
  const activeCount = active.length;
  active.forEach(p => {
    p.chips -= betAmount;
    // update chip display
    const chipEl = p.el.parentElement.querySelector('.chips');
    chipEl.textContent = `Chips: $${p.chips}`;
  });
  pot += betAmount * activeCount;
  log(`Each active player bets $${betAmount}. Pot is now $${pot}.`);
  nextRound();
});

foldButton.addEventListener('click', () => {
  playerFold(0);  // you (Player 1) fold
});
