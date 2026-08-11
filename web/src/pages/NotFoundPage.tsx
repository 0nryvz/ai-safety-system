import { Link } from 'react-router-dom'
import { ROUTE_PATHS } from '../app/routeConfig'

function NotFoundPage() {
  return (
    <main>
      <section>
        <p>404</p>
        <h1>Sayfa bulunamadı</h1>
        <p>Aradığınız sayfa mevcut değil veya taşınmış olabilir.</p>

        <Link to={ROUTE_PATHS.home}>Ana sayfaya dön</Link>
      </section>
    </main>
  )
}

export default NotFoundPage
