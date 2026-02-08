import * as React from "react";

type ErrorBoundaryProps = { fallback: React.ReactNode; children: React.ReactNode };

class ErrorBoundary extends React.Component<ErrorBoundaryProps> {
  state: { hasError: boolean };

  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    // Update state so the next render will show the fallback UI.
    return { hasError: true };
  }

  // componentDidCatch(error: Error, info: React.ErrorInfo) {
  //   logErrorToMyService(
  //     error,
  //     // Example "componentStack":
  //     //   in ComponentThatThrows (created by App)
  //     //   in ErrorBoundary (created by App)
  //     //   in div (created by App)
  //     //   in App
  //     info.componentStack,
  //     // Warning: `captureOwnerStack` is not available in production.
  //     React.captureOwnerStack(),
  //   );
  // }

  render() {
    if (this.state.hasError) {
      // You can render any custom fallback UI
      return this.props.fallback;
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
