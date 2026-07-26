import React from 'react';
import { createBrowserRouter } from 'react-router-dom';
import App from '@/App';

/**
 * React Router configuration with initial placeholder route.
 * Additional feature routes will be integrated in subsequent phases.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
  },
  {
    path: '*',
    element: (
      <div className="flex min-h-screen items-center justify-center bg-slate-950 text-slate-100">
        <div className="text-center p-8 bg-slate-900 rounded-xl border border-slate-800 shadow-2xl">
          <h1 className="text-4xl font-extrabold text-amber-500 mb-2">404</h1>
          <p className="text-slate-400">Page Not Found</p>
        </div>
      </div>
    ),
  },
]);

export default router;
