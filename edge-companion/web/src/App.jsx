import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout'
import PatientsPage from './pages/PatientsPage'
import PatientDetailPage from './pages/PatientDetailPage'
import PatientFormPage from './pages/PatientFormPage'
import ChatPage from './pages/ChatPage'
import CloudPage from './pages/CloudPage'
import SettingsPage from './pages/SettingsPage'
import NewEntryPage from './pages/NewEntryPage'

export default function App() {
  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Navigate to="/patients" replace />} />
        <Route path="/patients" element={<PatientsPage />} />
        <Route path="/patients/new" element={<PatientFormPage />} />
        <Route path="/patients/:id/edit" element={<PatientFormPage />} />
        <Route path="/patients/:id" element={<PatientDetailPage />} />
        <Route path="/patients/:id/entries/new" element={<NewEntryPage />} />
        <Route path="/patients/:id/chat" element={<ChatPage />} />
        <Route path="/patients/:id/diagnosis" element={<PatientDetailPage showDiagnosis />} />
        <Route path="/chat" element={<ChatPage />} />
        <Route path="/cloud/:patientId" element={<CloudPage />} />
        <Route path="/settings" element={<SettingsPage />} />
      </Routes>
    </Layout>
  )
}
