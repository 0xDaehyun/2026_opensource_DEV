const ui = {
  status: document.querySelector('#server-status'),
  virtualTime: document.querySelector('#virtual-time'),
  cash: document.querySelector('#cash'),
  lockedCash: document.querySelector('#locked-cash'),
  positionQuantity: document.querySelector('#position-quantity'),
  positionSymbol: document.querySelector('#position-symbol'),
  totalOrders: document.querySelector('#total-orders'),
  orderBreakdown: document.querySelector('#order-breakdown'),
  scenarioName: document.querySelector('#scenario-name'),
  fillSetting: document.querySelector('#fill-setting'),
  ordersBody: document.querySelector('#orders-body'),
  eventList: document.querySelector('#event-list'),
  lastUpdated: document.querySelector('#last-updated'),
  actionMessage: document.querySelector('#action-message'),
  buyButton: document.querySelector('#buy-button'),
  cancelButton: document.querySelector('#cancel-button'),
  refreshButton: document.querySelector('#refresh-button')
};

const stateLabels = {
  ACCEPTED: '접수',
  PARTIALLY_FILLED: '부분체결',
  FILLED: '체결완료',
  CANCELLED: '취소',
  REJECTED: '거부',
  UNKNOWN: '확인 중'
};

const eventLabels = {
  ORDER_ACCEPTED: '주문 접수',
  ORDER_REJECTED: '주문 거부',
  PARTIAL_FILL: '부분 체결',
  FILL: '전량 체결',
  ORDER_CANCELLED: '주문 취소'
};

let latestSnapshot = null;
let latestLsOrderNumber = window.localStorage.getItem('stockmock.latestLsOrderNumber');
let accessToken = null;

const money = value => `${new Intl.NumberFormat('ko-KR').format(value ?? 0)}원`;
const number = value => new Intl.NumberFormat('ko-KR').format(value ?? 0);
const clockText = value => value ? new Date(value).toLocaleString('ko-KR', { hour12: false }) : '-';

function setText(target, value) {
  target.textContent = value;
}

function cell(value, className = '') {
  const td = document.createElement('td');
  td.textContent = value;
  if (className) td.className = className;
  return td;
}

function showMessage(message, type = '') {
  ui.actionMessage.textContent = message;
  ui.actionMessage.className = `action-message ${type}`.trim();
}

function render(snapshot) {
  latestSnapshot = snapshot;
  setText(ui.status, `● ${snapshot.serverStatus}`);
  ui.status.className = 'status-chip status-running';
  setText(ui.virtualTime, `가상 시각 ${clockText(snapshot.virtualTime)}`);
  setText(ui.cash, money(snapshot.account.cash));
  setText(ui.lockedCash, money(snapshot.account.lockedCash));

  const totalPosition = snapshot.account.positions.reduce((sum, item) => sum + item.quantity, 0);
  setText(ui.positionQuantity, `${number(totalPosition)}주`);
  setText(ui.positionSymbol, snapshot.account.positions.length
    ? snapshot.account.positions.map(item => `${item.symbol} ${number(item.quantity)}주`).join(' · ')
    : '보유 종목 없음');

  const counts = snapshot.orderCounts;
  setText(ui.totalOrders, `${number(counts.total)}건`);
  setText(ui.orderBreakdown, `부분체결 ${counts.partiallyFilled} · 체결 ${counts.filled} · 취소 ${counts.cancelled}`);
  setText(ui.scenarioName, snapshot.scenario.name);
  const fillAmount = snapshot.scenario.fillRatio != null
    ? `${Math.round(snapshot.scenario.fillRatio * 100)}%`
    : `${number(snapshot.scenario.fillQuantity)}주`;
  setText(ui.fillSetting, `${fillAmount} · ${snapshot.scenario.fillDelay}`);
  setText(document.querySelector('#count-accepted'), counts.accepted);
  setText(document.querySelector('#count-partial'), counts.partiallyFilled);
  setText(document.querySelector('#count-filled'), counts.filled);
  setText(document.querySelector('#count-cancelled'), counts.cancelled);
  setText(document.querySelector('#count-rejected'), counts.rejected);
  setText(ui.lastUpdated, `마지막 갱신 ${new Date().toLocaleTimeString('ko-KR', { hour12: false })}`);

  renderOrders(snapshot.orders);
  renderEvents(snapshot.events);
}

function renderOrders(orders) {
  ui.ordersBody.replaceChildren();
  if (!orders.length) {
    const tr = document.createElement('tr');
    const td = cell('아직 주문이 없습니다.', 'empty-cell');
    td.colSpan = 7;
    tr.append(td);
    ui.ordersBody.append(tr);
    return;
  }

  orders.forEach(order => {
    const tr = document.createElement('tr');
    tr.append(cell(order.orderId), cell(order.symbol));

    const statusCell = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = `state-badge ${order.state}`;
    badge.textContent = stateLabels[order.state] ?? order.state;
    statusCell.append(badge);
    tr.append(statusCell);

    tr.append(
      cell(`${number(order.quantity)}주`, 'number'),
      cell(`${number(order.filledQuantity)}주`, 'number'),
      cell(`${number(order.remainingQuantity)}주`, 'number'),
      cell(money(order.price), 'number')
    );
    ui.ordersBody.append(tr);
  });
}

function renderEvents(events) {
  ui.eventList.replaceChildren();
  if (!events.length) {
    const empty = document.createElement('p');
    empty.className = 'empty-state';
    empty.textContent = '주문을 실행하면 이벤트가 여기에 표시됩니다.';
    ui.eventList.append(empty);
    return;
  }

  events.forEach(event => {
    const row = document.createElement('div');
    row.className = 'event-row';

    const seq = document.createElement('span');
    seq.className = 'event-seq';
    seq.textContent = `#${String(event.seq).padStart(4, '0')}`;
    const type = document.createElement('span');
    type.className = 'event-type';
    type.textContent = eventLabels[event.type] ?? event.type;
    const order = document.createElement('span');
    order.className = 'event-order';
    order.textContent = event.orderId ?? '-';
    const time = document.createElement('span');
    time.className = 'event-time';
    time.textContent = clockText(event.virtualTime);

    row.append(seq, type, order, time);
    ui.eventList.append(row);
  });
}

async function refresh() {
  try {
    const response = await fetch('/mock/dashboard', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    render(await response.json());
  } catch (error) {
    setText(ui.status, '● DISCONNECTED');
    ui.status.className = 'status-chip status-error';
    showMessage(`대시보드 연결 실패: ${error.message}`, 'error');
  }
}

async function requestJson(url, body) {
  const token = await ensureToken();
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(body)
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.rsp_msg ?? `HTTP ${response.status}`);
  return data;
}

async function ensureToken() {
  if (accessToken) return accessToken;
  const form = new URLSearchParams({
    grant_type: 'client_credentials',
    appkey: 'dashboard',
    appsecretkey: 'dashboard-secret'
  });
  const response = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: form
  });
  const data = await response.json();
  if (!response.ok || !data.access_token) {
    throw new Error(data.rsp_msg ?? 'Mock 토큰을 발급받지 못했습니다.');
  }
  accessToken = data.access_token;
  return accessToken;
}

async function buyDemo() {
  setBusy(true);
  try {
    const result = await requestJson('/stock/order', {
      CSPAT00601InBlock1: {
        AcntNo: '12345678901',
        IsuNo: '005930',
        OrdQty: 100,
        OrdPrc: 70000,
        BnsTpCode: '2',
        clientOrderId: `dashboard-${Date.now()}`
      }
    });
    if (result.rsp_cd !== '00040') throw new Error(result.rsp_msg ?? '주문이 거부되었습니다.');
    latestLsOrderNumber = String(result.CSPAT00601OutBlock2.OrdNo);
    window.localStorage.setItem('stockmock.latestLsOrderNumber', latestLsOrderNumber);
    showMessage(`주문 ${latestLsOrderNumber} 접수 완료 · 시나리오에 따라 체결됩니다.`, 'success');
    await refresh();
  } catch (error) {
    showMessage(error.message, 'error');
  } finally {
    setBusy(false);
  }
}

async function cancelDemo() {
  if (!latestLsOrderNumber) {
    showMessage('이 화면에서 먼저 데모 매수 주문을 실행해주세요.', 'error');
    return;
  }
  const target = latestSnapshot?.orders.find(order => !['FILLED', 'CANCELLED', 'REJECTED'].includes(order.state));
  const remainingQuantity = target?.remainingQuantity ?? 70;

  setBusy(true);
  try {
    const result = await requestJson('/stock/order', {
      CSPAT00801InBlock1: {
        AcntNo: '12345678901',
        OrgOrdNo: Number(latestLsOrderNumber),
        IsuNo: '005930',
        OrdQty: remainingQuantity
      }
    });
    if (result.rsp_cd !== '00156') throw new Error(result.rsp_msg ?? '취소가 거부되었습니다.');
    showMessage(`주문 ${latestLsOrderNumber}의 미체결 수량을 취소했습니다.`, 'success');
    await refresh();
  } catch (error) {
    showMessage(error.message, 'error');
  } finally {
    setBusy(false);
  }
}

function setBusy(busy) {
  ui.buyButton.disabled = busy;
  ui.cancelButton.disabled = busy;
  ui.refreshButton.disabled = busy;
}

ui.buyButton.addEventListener('click', buyDemo);
ui.cancelButton.addEventListener('click', cancelDemo);
ui.refreshButton.addEventListener('click', refresh);

refresh();
window.setInterval(refresh, 2000);
