import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = {
    hasError: false,
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return {
      hasError: true,
    }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Uygulama hatası yakalandı:', error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      return (
        <main role="alert">
          <h1>Bir hata oluştu</h1>
          <p>Sayfayı yenileyerek tekrar deneyin.</p>
        </main>
      )
    }

    return this.props.children
  }
}

export default ErrorBoundary
