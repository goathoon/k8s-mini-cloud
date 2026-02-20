import { useMemo, useState } from "react";

const initialLog = [
  {
    at: new Date().toLocaleTimeString(),
    label: "mini-cloud Console",
    payload: "Ready. API Base URL을 확인하고 요청을 실행하세요.",
  },
];

function Card({ title, danger = false, children }) {
  return (
    <article className={`card ${danger ? "danger" : ""}`}>
      <h2>{title}</h2>
      {children}
    </article>
  );
}

export default function App() {
  const [apiBaseUrl, setApiBaseUrl] = useState(
    import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
  );
  const [logs, setLogs] = useState(initialLog);

  const [dbForm, setDbForm] = useState({ name: "", namespace: "" });
  const [appForm, setAppForm] = useState({
    name: "",
    namespace: "",
    image: "nginx:latest",
    port: 80,
    replicas: 1,
    databaseRef: "",
  });
  const [appGetForm, setAppGetForm] = useState({ name: "", namespace: "" });
  const [appDeleteForm, setAppDeleteForm] = useState({ name: "", namespace: "" });

  const consoleText = useMemo(() => {
    return logs
      .map((item) => {
        const body =
          typeof item.payload === "string"
            ? item.payload
            : JSON.stringify(item.payload, null, 2);
        return `[${item.at}] ${item.label}\n${body}`;
      })
      .join("\n\n");
  }, [logs]);

  const pushLog = (label, payload) => {
    setLogs((prev) => [
      {
        at: new Date().toLocaleTimeString(),
        label,
        payload,
      },
      ...prev,
    ]);
  };

  const callApi = async ({ method, path, body }) => {
    const base = apiBaseUrl.trim().replace(/\/$/, "");
    const url = `${base}${path}`;
    const options = {
      method,
      headers: { "Content-Type": "application/json" },
    };
    if (body) options.body = JSON.stringify(body);

    try {
      const response = await fetch(url, options);
      const text = await response.text();
      const data = text ? JSON.parse(text) : null;
      if (!response.ok) {
        pushLog(`${method} ${path} -> ${response.status}`, data || text);
        return;
      }
      pushLog(`${method} ${path} -> ${response.status}`, data);
    } catch (error) {
      pushLog(`${method} ${path} -> NETWORK ERROR`, String(error));
    }
  };

  return (
    <>
      <div className="bg-shape bg-shape-a" />
      <div className="bg-shape bg-shape-b" />
      <main className="layout">
        <header className="hero">
          <p className="eyebrow">Local IDP Console</p>
          <h1>mini-cloud API Dashboard</h1>
          <p className="desc">
            Kubernetes 리소스를 직접 만지지 않고 API로 App/DB를 프로비저닝합니다.
          </p>
          <div className="api-target">
            <label htmlFor="apiBaseUrl">API Base URL</label>
            <input
              id="apiBaseUrl"
              value={apiBaseUrl}
              onChange={(e) => setApiBaseUrl(e.target.value)}
            />
          </div>
        </header>

        <section className="grid">
          <Card title="Database 생성">
            <form
              className="form"
              onSubmit={async (e) => {
                e.preventDefault();
                await callApi({
                  method: "POST",
                  path: "/v1/databases",
                  body: dbForm,
                });
              }}
            >
              <label>
                Database Name
                <input
                  value={dbForm.name}
                  onChange={(e) => setDbForm({ ...dbForm, name: e.target.value })}
                  placeholder="pg-main"
                  required
                />
              </label>
              <label>
                Namespace
                <input
                  value={dbForm.namespace}
                  onChange={(e) => setDbForm({ ...dbForm, namespace: e.target.value })}
                  placeholder="demo"
                  required
                />
              </label>
              <button type="submit">POST /v1/databases</button>
            </form>
          </Card>

          <Card title="App 생성">
            <form
              className="form"
              onSubmit={async (e) => {
                e.preventDefault();
                const body = {
                  name: appForm.name,
                  namespace: appForm.namespace,
                  image: appForm.image,
                  port: Number(appForm.port),
                  replicas: Number(appForm.replicas),
                };
                if (appForm.databaseRef.trim()) {
                  body.databaseRef = appForm.databaseRef.trim();
                }
                await callApi({ method: "POST", path: "/v1/apps", body });
              }}
            >
              <label>
                Name
                <input
                  value={appForm.name}
                  onChange={(e) => setAppForm({ ...appForm, name: e.target.value })}
                  placeholder="hello"
                  required
                />
              </label>
              <label>
                Namespace
                <input
                  value={appForm.namespace}
                  onChange={(e) => setAppForm({ ...appForm, namespace: e.target.value })}
                  placeholder="demo"
                  required
                />
              </label>
              <label>
                Image
                <input
                  value={appForm.image}
                  onChange={(e) => setAppForm({ ...appForm, image: e.target.value })}
                  required
                />
              </label>
              <label>
                Port
                <input
                  type="number"
                  min="1"
                  max="65535"
                  value={appForm.port}
                  onChange={(e) => setAppForm({ ...appForm, port: e.target.value })}
                  required
                />
              </label>
              <label>
                Replicas
                <input
                  type="number"
                  min="1"
                  value={appForm.replicas}
                  onChange={(e) => setAppForm({ ...appForm, replicas: e.target.value })}
                  required
                />
              </label>
              <label>
                Database Ref
                <input
                  value={appForm.databaseRef}
                  onChange={(e) => setAppForm({ ...appForm, databaseRef: e.target.value })}
                  placeholder="pg-main (optional)"
                />
              </label>
              <button type="submit">POST /v1/apps</button>
            </form>
          </Card>

          <Card title="App 상태 조회">
            <form
              className="form"
              onSubmit={async (e) => {
                e.preventDefault();
                await callApi({
                  method: "GET",
                  path: `/v1/apps/${encodeURIComponent(appGetForm.name)}?namespace=${encodeURIComponent(
                    appGetForm.namespace,
                  )}`,
                });
              }}
            >
              <label>
                Name
                <input
                  value={appGetForm.name}
                  onChange={(e) => setAppGetForm({ ...appGetForm, name: e.target.value })}
                  placeholder="hello"
                  required
                />
              </label>
              <label>
                Namespace
                <input
                  value={appGetForm.namespace}
                  onChange={(e) =>
                    setAppGetForm({ ...appGetForm, namespace: e.target.value })
                  }
                  placeholder="demo"
                  required
                />
              </label>
              <button type="submit">GET /v1/apps/{'{name}'}</button>
            </form>
          </Card>

          <Card title="App 삭제" danger>
            <form
              className="form"
              onSubmit={async (e) => {
                e.preventDefault();
                await callApi({
                  method: "DELETE",
                  path: `/v1/apps/${encodeURIComponent(
                    appDeleteForm.name,
                  )}?namespace=${encodeURIComponent(appDeleteForm.namespace)}`,
                });
              }}
            >
              <label>
                Name
                <input
                  value={appDeleteForm.name}
                  onChange={(e) =>
                    setAppDeleteForm({ ...appDeleteForm, name: e.target.value })
                  }
                  placeholder="hello"
                  required
                />
              </label>
              <label>
                Namespace
                <input
                  value={appDeleteForm.namespace}
                  onChange={(e) =>
                    setAppDeleteForm({ ...appDeleteForm, namespace: e.target.value })
                  }
                  placeholder="demo"
                  required
                />
              </label>
              <button type="submit">DELETE /v1/apps/{'{name}'}</button>
            </form>
          </Card>
        </section>

        <section className="console-wrap">
          <div className="console-header">
            <h3>Response Console</h3>
            <button type="button" className="ghost" onClick={() => setLogs([])}>
              Clear
            </button>
          </div>
          <pre className="console">{consoleText}</pre>
        </section>
      </main>
    </>
  );
}
