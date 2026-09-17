const modules = [
  'Identity',
  'Catalog',
  'Communication',
  'Commerce',
  'Payment',
  'Fulfillment',
  'Trust & Safety',
  'Platform',
]

export function App() {
  return (
    <main className="app-shell">
      <section className="hero" aria-labelledby="project-title">
        <span className="status">Foundation · In progress</span>
        <p className="eyebrow">Old but Gold</p>
        <h1 id="project-title">O.G Shop</h1>
        <p className="lead">Nền tảng mua bán đồ cũ đáng tin cậy.</p>
      </section>

      <section className="module-section" aria-labelledby="module-heading">
        <div>
          <p className="eyebrow">Modular monolith</p>
          <h2 id="module-heading">Nền tảng đã sẵn sàng để phát triển theo module</h2>
        </div>
        <ul className="module-grid">
          {modules.map((module) => (
            <li key={module}>{module}</li>
          ))}
        </ul>
      </section>
    </main>
  )
}
