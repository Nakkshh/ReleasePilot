export function verdictKind(value) {
  switch (String(value || '').toUpperCase()) {
    case 'READY':
      return 'success'
    case 'NOT_READY':
      return 'danger'
    default:
      return 'neutral'
  }
}