import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// 성능 측정을 원하면 함수를 전달
// 예: reportWebVitals(console.log)
// 또는 Google Analytics 등으로 전송: reportWebVitals(sendToAnalytics)
reportWebVitals();