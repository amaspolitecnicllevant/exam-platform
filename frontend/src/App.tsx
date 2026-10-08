import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ConfiguracioProvider } from './context/ConfiguracioContext'
import ProtectedRoute from './components/ProtectedRoute'
import BloqueigAltresPestanyes from './components/BloqueigAltresPestanyes'
import LoginPage from './pages/LoginPage'
import OAuthCallbackPage from './pages/OAuthCallbackPage'
import UsersPage from './pages/admin/UsersPage'
import EstruturaPage from './pages/admin/EstruturaPage'
import MatriculesPage from './pages/admin/MatriculesPage'
import AulesPage from './pages/admin/AulesPage'
import ConfiguracioPage from './pages/admin/ConfiguracioPage'
import AuditPage from './pages/admin/AuditPage'
import EmmagatzematgePage from './pages/admin/EmmagatzematgePage'
import ConvitsPage from './pages/professor/ConvitsPage'
import AcceptarConvitPage from './pages/invitacio/AcceptarConvitPage'
import ExamsPage from './pages/professor/ExamsPage'
import ExamCreatePage from './pages/professor/ExamCreatePage'
import ExamSintaxiPage from './pages/professor/ExamSintaxiPage'
import CorrectionPage from './pages/professor/CorrectionPage'
import StatsPage from './pages/professor/StatsPage'
import CopiesPage from './pages/professor/CopiesPage'
import MonitorPage from './pages/professor/MonitorPage'
import ExamPreviewPage from './pages/professor/ExamPreviewPage'
import GrupsPage from './pages/professor/GrupsPage'
import StudentExamList from './pages/student/ExamListPage'
import HistorialPage from './pages/student/HistorialPage'
import ExamTakePage from './pages/student/ExamTakePage'
import ExamResultsPage from './pages/student/ExamResultsPage'

function Home() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" />
  if (user.role === 'ADMIN')      return <Navigate to="/admin/users" />
  if (user.role === 'PROFESSOR')  return <Navigate to="/professor/exams" />
  return <Navigate to="/student/exams" />
}

export default function App() {
  return (
    <ConfiguracioProvider>
    <AuthProvider>
      <BloqueigAltresPestanyes />
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/oauth-callback" element={<OAuthCallbackPage />} />
          <Route path="/" element={<Home />} />
          <Route path="/admin/users" element={
            <ProtectedRoute roles={['ADMIN','PROFESSOR']}><UsersPage /></ProtectedRoute>} />
          <Route path="/invitacio/:token" element={<AcceptarConvitPage />} />
          <Route path="/professor/convits" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><ConvitsPage /></ProtectedRoute>} />
          <Route path="/admin/estructura" element={
            <ProtectedRoute roles={['ADMIN']}><EstruturaPage /></ProtectedRoute>} />
          <Route path="/admin/matricules" element={
            <ProtectedRoute roles={['ADMIN']}><MatriculesPage /></ProtectedRoute>} />
          <Route path="/admin/aules" element={
            <ProtectedRoute roles={['ADMIN']}><AulesPage /></ProtectedRoute>} />
          <Route path="/admin/configuracio" element={
            <ProtectedRoute roles={['ADMIN']}><ConfiguracioPage /></ProtectedRoute>} />
          <Route path="/admin/audit" element={
            <ProtectedRoute roles={['ADMIN']}><AuditPage /></ProtectedRoute>} />
          <Route path="/admin/emmagatzematge" element={
            <ProtectedRoute roles={['ADMIN']}><EmmagatzematgePage /></ProtectedRoute>} />
          <Route path="/professor/exams" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><ExamsPage /></ProtectedRoute>} />
          <Route path="/professor/exams/new" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><ExamCreatePage /></ProtectedRoute>} />
          <Route path="/professor/exams/sintaxi" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><ExamSintaxiPage /></ProtectedRoute>} />
          <Route path="/professor/grups" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><GrupsPage /></ProtectedRoute>} />
          <Route path="/professor/exams/:examId/preview" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><ExamPreviewPage /></ProtectedRoute>} />
          <Route path="/professor/exams/:examId/copies" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><CopiesPage /></ProtectedRoute>} />
          <Route path="/professor/exams/:examId/stats" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><StatsPage /></ProtectedRoute>} />
          <Route path="/professor/exams/:examId/corrections" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><CorrectionPage /></ProtectedRoute>} />
          <Route path="/professor/exams/:examId/monitor" element={
            <ProtectedRoute roles={['PROFESSOR','ADMIN']}><MonitorPage /></ProtectedRoute>} />
          <Route path="/student/historial" element={
            <ProtectedRoute roles={['STUDENT']}><HistorialPage /></ProtectedRoute>} />
          <Route path="/student/exams" element={
            <ProtectedRoute roles={['STUDENT']}><StudentExamList /></ProtectedRoute>} />
          <Route path="/student/exams/:examId" element={
            <ProtectedRoute roles={['STUDENT']}><ExamTakePage /></ProtectedRoute>} />
          <Route path="/student/sessions/:sessionId/results" element={
            <ProtectedRoute roles={['STUDENT']}><ExamResultsPage /></ProtectedRoute>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
    </ConfiguracioProvider>
  )
}
