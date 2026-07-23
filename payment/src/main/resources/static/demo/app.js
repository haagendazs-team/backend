const state = {
    memberId: "1",
    workspaceId: "1",
    clientKey: "",
    products: [],
    orders: [],
    selectedProductId: "",
    currentOrder: null,
    billingPrepare: null,
    selectedOrderNo: "",
};

const $ = (id) => document.getElementById(id);

const fields = {
    memberId: $("memberIdInput"),
    workspaceId: $("workspaceIdInput"),
    clientKey: $("clientKeyInput"),
    selectedProductId: $("selectedProductIdInput"),
    planChangeProductId: $("planChangeProductIdInput"),
    orderIdempotencyKey: $("orderIdempotencyKeyInput"),
    authKey: $("authKeyInput"),
    apiStatus: $("apiStatus"),
    products: $("products"),
    subscriptionBox: $("subscriptionBox"),
    scheduledChangeBox: $("scheduledChangeBox"),
    billingPrepareBox: $("billingPrepareBox"),
    currentOrderBox: $("currentOrderBox"),
    planChangeBox: $("planChangeBox"),
    orders: $("orders"),
    orderDetailBox: $("orderDetailBox"),
    logBox: $("logBox"),
};

function init() {
    loadConfig();
    bindEvents();
    handleRedirectParams();
    refreshCatalog();
    refreshOrders();
}

function bindEvents() {
    $("saveConfigBtn").addEventListener("click", saveConfig);
    $("refreshCatalogBtn").addEventListener("click", refreshCatalog);
    $("newAttemptBtn").addEventListener("click", newOrderAttempt);
    $("createOrderBtn").addEventListener("click", () => createOrder(false));
    $("createOrderTwiceBtn").addEventListener("click", () => createOrder(true));
    $("prepareBillingBtn").addEventListener("click", prepareBilling);
    $("requestBillingAuthBtn").addEventListener("click", requestBillingAuth);
    $("confirmBillingOnlyBtn").addEventListener("click", confirmBillingOnly);
    $("confirmAndPayBtn").addEventListener("click", confirmAndPay);
    $("payRegisteredBtn").addEventListener("click", payRegisteredBilling);
    $("changePlanBtn").addEventListener("click", changePlan);
    $("cancelPlanChangeBtn").addEventListener("click", cancelPlanChange);
    $("refreshOrdersBtn").addEventListener("click", refreshOrders);
    $("clearLogBtn").addEventListener("click", () => fields.logBox.textContent = "");
}

function loadConfig() {
    const saved = JSON.parse(localStorage.getItem("paymentDemoConfig") || "{}");
    state.memberId = saved.memberId || "1";
    state.workspaceId = saved.workspaceId || "1";
    state.clientKey = saved.clientKey || "";
    state.selectedProductId = saved.selectedProductId || "";

    fields.memberId.value = state.memberId;
    fields.workspaceId.value = state.workspaceId;
    fields.clientKey.value = state.clientKey;
    fields.selectedProductId.value = state.selectedProductId;
    fields.planChangeProductId.value = state.selectedProductId;
    fields.orderIdempotencyKey.value = saved.idempotencyKey || makeIdempotencyKey();
}

function saveConfig() {
    state.memberId = fields.memberId.value.trim();
    state.workspaceId = fields.workspaceId.value.trim();
    state.clientKey = fields.clientKey.value.trim();
    state.selectedProductId = fields.selectedProductId.value.trim();

    localStorage.setItem("paymentDemoConfig", JSON.stringify({
        memberId: state.memberId,
        workspaceId: state.workspaceId,
        clientKey: state.clientKey,
        selectedProductId: state.selectedProductId,
        idempotencyKey: fields.orderIdempotencyKey.value.trim(),
    }));
    log("설정을 저장했습니다.");
}

function handleRedirectParams() {
    const params = new URLSearchParams(window.location.search);
    const authKey = params.get("authKey");
    const customerKey = params.get("customerKey");
    const code = params.get("code");
    const message = params.get("message");

    if (authKey) {
        fields.authKey.value = authKey;
        log("Toss billing auth success", { authKey, customerKey });
    }
    if (code || message) {
        log("Toss billing auth failed", { code, message });
    }
}

async function refreshCatalog() {
    await Promise.allSettled([
        loadProducts(),
        loadSubscription(),
        loadScheduledChange(),
    ]);
}

async function loadProducts() {
    const response = await api("/api/v1/products?type=SUBSCRIPTION");
    state.products = response.data || [];
    renderProducts();
    log("상품 목록 조회", state.products);
}

async function loadSubscription() {
    try {
        const response = await api(`/api/v1/payments/workspaces/${state.workspaceId}/subscriptions`);
        renderJson(fields.subscriptionBox, response.data);
    } catch (error) {
        renderJson(fields.subscriptionBox, error.body || error.message);
    }
}

async function loadScheduledChange() {
    try {
        const response = await api(`/api/v1/payments/workspaces/${state.workspaceId}/subscriptions/plan-change`);
        renderJson(fields.scheduledChangeBox, response.data);
    } catch (error) {
        renderJson(fields.scheduledChangeBox, error.body || error.message);
    }
}

function renderProducts() {
    fields.products.innerHTML = "";

    if (!state.products.length) {
        fields.products.textContent = "상품이 없습니다.";
        return;
    }

    state.products.forEach((product) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `product-card ${String(product.productId) === String(fields.selectedProductId.value) ? "active" : ""}`;
        button.innerHTML = `
            <strong>${escapeHtml(product.name)}</strong>
            <span class="meta">
                <span class="pill">ID ${product.productId}</span>
                <span class="pill">${formatMoney(product.price)}</span>
                <span class="pill">${product.detail?.type || product.productType}</span>
                <span class="pill">${product.status}</span>
            </span>
        `;
        button.addEventListener("click", () => {
            fields.selectedProductId.value = product.productId;
            fields.planChangeProductId.value = product.productId;
            saveConfig();
            renderProducts();
        });
        fields.products.appendChild(button);
    });
}

function newOrderAttempt() {
    fields.orderIdempotencyKey.value = makeIdempotencyKey();
    state.currentOrder = null;
    renderJson(fields.currentOrderBox, "-");
    saveConfig();
}

async function createOrder(twice) {
    const request = () => api(
        `/api/v1/payments/workspaces/${state.workspaceId}/orders/subscription`,
        {
            method: "POST",
            headers: {
                "X-Member-Id": state.memberId,
                "Idempotency-Key": fields.orderIdempotencyKey.value.trim(),
            },
            body: { productId: Number(fields.selectedProductId.value) },
        }
    );

    const first = await request();
    state.currentOrder = first.data;
    renderJson(fields.currentOrderBox, first.data);
    log("주문 생성", first.data);

    if (twice) {
        const second = await request();
        log("중복 주문 생성 요청 결과", second.data);
        renderJson(fields.currentOrderBox, {
            first: first.data,
            second: second.data,
            sameOrder: first.data?.orderNo === second.data?.orderNo,
        });
    }

    if (state.currentOrder?.customerKey) {
        state.billingPrepare = { customerKey: state.currentOrder.customerKey };
        renderJson(fields.billingPrepareBox, state.billingPrepare);
    }

    await refreshOrders();
}

async function prepareBilling() {
    const response = await api("/api/v1/payments/billing-methods/prepare", {
        method: "POST",
        headers: { "X-Member-Id": state.memberId },
    });
    state.billingPrepare = response.data;
    renderJson(fields.billingPrepareBox, response.data);
    log("자동결제 등록 준비", response.data);
}

async function requestBillingAuth() {
    if (!state.clientKey) {
        throw new Error("Toss Client Key를 입력하고 저장하세요.");
    }
    if (!state.billingPrepare?.customerKey) {
        await prepareBilling();
    }

    const tossPayments = TossPayments(state.clientKey);
    const payment = tossPayments.payment({ customerKey: state.billingPrepare.customerKey });
    const url = new URL(window.location.href);
    url.search = "";
    localStorage.setItem("paymentDemoLastOrderNo", state.currentOrder?.orderNo || "");

    await payment.requestBillingAuth({
        method: "CARD",
        successUrl: url.toString(),
        failUrl: url.toString(),
    });
}

async function confirmBillingOnly() {
    const response = await api("/api/v1/payments/billing-methods/confirm", {
        method: "POST",
        headers: { "X-Member-Id": state.memberId },
        body: { authKey: fields.authKey.value.trim() },
    });
    log("자동결제 수단 등록 완료", response);
}

async function confirmAndPay() {
    const orderNo = currentOrderNo();
    const response = await api("/api/v1/payments/billing-methods/confirm-and-pay", {
        method: "POST",
        headers: { "X-Member-Id": state.memberId },
        body: {
            authKey: fields.authKey.value.trim(),
            orderNo,
        },
    });
    log("자동결제 수단 등록 + 주문 결제 완료", response);
    await refreshOrders();
}

async function payRegisteredBilling() {
    const orderNo = currentOrderNo();
    const response = await api("/api/v1/payments/billing", {
        method: "POST",
        headers: { "X-Member-Id": state.memberId },
        body: { orderNo },
    });
    log("등록된 자동결제 수단 결제 완료", response);
    await refreshOrders();
}

async function changePlan() {
    const response = await api(`/api/v1/payments/workspaces/${state.workspaceId}/subscriptions/plan-change`, {
        method: "POST",
        headers: { "X-Member-Id": state.memberId },
        body: { productId: Number(fields.planChangeProductId.value) },
    });
    renderJson(fields.planChangeBox, response.data);
    log("플랜 변경 요청", response.data);
    await loadScheduledChange();
}

async function cancelPlanChange() {
    const response = await api(`/api/v1/payments/workspaces/${state.workspaceId}/subscriptions/plan-change`, {
        method: "DELETE",
        headers: { "X-Member-Id": state.memberId },
    });
    log("예약 플랜 변경 취소", response);
    await loadScheduledChange();
}

async function refreshOrders() {
    try {
        const response = await api(`/api/v1/payments/workspaces/${state.workspaceId}/orders`, {
            headers: { "X-Member-Id": state.memberId },
        });
        renderOrders(response.data || []);
        log("주문 목록 조회", response.data || []);
    } catch (error) {
        fields.orders.textContent = "주문 조회 실패";
        log("주문 목록 조회 실패", error.body || error.message);
    }
}

function renderOrders(orders) {
    state.orders = orders;
    fields.orders.innerHTML = "";

    if (!orders.length) {
        fields.orders.textContent = "주문이 없습니다.";
        return;
    }

    orders.forEach((order) => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = `order-card ${state.selectedOrderNo === order.orderNo ? "active" : ""}`;
        button.innerHTML = `
            <strong>${escapeHtml(order.orderName || order.orderNo)}</strong>
            <span class="meta">
                <span class="pill">${order.orderStatus}</span>
                <span class="pill">${formatMoney(order.totalAmount)}</span>
                <span class="pill">${order.orderNo}</span>
            </span>
        `;
        button.addEventListener("click", () => loadOrderDetail(order.orderNo));
        fields.orders.appendChild(button);
    });
}

async function loadOrderDetail(orderNo) {
    state.selectedOrderNo = orderNo;
    const response = await api(`/api/v1/payments/workspaces/${state.workspaceId}/orders/${orderNo}`, {
        headers: { "X-Member-Id": state.memberId },
    });
    renderJson(fields.orderDetailBox, response.data);
    log("주문 상세 조회", response.data);
    state.currentOrder = {
        orderNo: response.data.orderNo,
        orderId: response.data.orderId,
        amount: response.data.totalAmount,
    };
    renderOrders(state.orders);
}

async function api(path, options = {}) {
    fields.apiStatus.textContent = "loading";
    fields.apiStatus.style.color = "#92400e";
    fields.apiStatus.style.background = "#fef3c7";

    const headers = {
        "Content-Type": "application/json",
        ...(options.headers || {}),
    };

    const response = await fetch(path, {
        method: options.method || "GET",
        headers,
        body: options.body ? JSON.stringify(options.body) : undefined,
    });

    let body = null;
    const text = await response.text();
    if (text) {
        try {
            body = JSON.parse(text);
        } catch {
            body = text;
        }
    }

    fields.apiStatus.textContent = response.ok ? "ok" : "error";
    fields.apiStatus.style.color = response.ok ? "#047857" : "#b91c1c";
    fields.apiStatus.style.background = response.ok ? "#ecfdf5" : "#fee2e2";

    if (!response.ok || body?.success === false) {
        const error = new Error(body?.message || response.statusText);
        error.body = body;
        log("API 실패", { path, status: response.status, body });
        throw error;
    }

    return body;
}

function currentOrderNo() {
    const orderNo = state.currentOrder?.orderNo || localStorage.getItem("paymentDemoLastOrderNo");
    if (!orderNo) {
        throw new Error("먼저 주문을 생성하거나 주문 상세를 선택하세요.");
    }

    return orderNo;
}

function makeIdempotencyKey() {
    return `checkout-${crypto.randomUUID()}`;
}

function renderJson(element, value) {
    element.textContent = typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

function log(message, payload) {
    const time = new Date().toLocaleTimeString();
    const line = payload === undefined
        ? `[${time}] ${message}`
        : `[${time}] ${message}\n${JSON.stringify(payload, null, 2)}`;
    fields.logBox.textContent = `${line}\n\n${fields.logBox.textContent}`;
}

function formatMoney(value) {
    return `${Number(value || 0).toLocaleString("ko-KR")}원`;
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

window.addEventListener("error", (event) => {
    log("실행 오류", event.error?.message || event.message);
});

window.addEventListener("unhandledrejection", (event) => {
    log("요청 오류", event.reason?.body || event.reason?.message || event.reason);
});

init();
