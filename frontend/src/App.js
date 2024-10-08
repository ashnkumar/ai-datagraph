import React from 'react';
import { BrowserRouter as Router, Route, Routes, Navigate } from 'react-router-dom';
import CreateDataProvider from './pages/CreateDataProvider';
import ViewProviders from './pages/ViewProviders';
import ViewUpdates from './pages/ViewUpdates';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Navigate to="/create-data-provider" />} />
        <Route path="/create-data-provider" element={<CreateDataProvider />} />
        <Route path="/view-providers" element={<ViewProviders />} />
        <Route path="/view-updates" element={<ViewUpdates />} />
      </Routes>
    </Router>
  );
}

export default App;
