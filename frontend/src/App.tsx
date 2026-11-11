import { Link, Route, Routes } from 'react-router-dom';
import ApplicationsPage from './pages/ApplicationsPage';
import ApplicationPage from './pages/ApplicationPage';
import PackagePage from './pages/PackagePage';

export default function App() {
  return (
    <>
      <header className="topbar no-print">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden>Q</span>
          <span>
            Financing Assessment
            <small>Invoice-based credit review · prototype</small>
          </span>
        </Link>
      </header>
      <main className="page">
        <Routes>
          <Route path="/" element={<ApplicationsPage />} />
          <Route path="/applications/:id" element={<ApplicationPage />} />
          <Route path="/applications/:id/package" element={<PackagePage />} />
          <Route path="*" element={<div className="empty">Page not found. <Link to="/">Go home</Link></div>} />
        </Routes>
      </main>
    </>
  );
}
