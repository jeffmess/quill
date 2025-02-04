package io.getquill.norm

import io.getquill.ast._

object ApplyMap {

  private def isomorphic(e: Ast, c: Ast, alias: Ident) =
    BetaReduction(e, alias -> c) == c

  object DetachableMap {
    def unapply(ast: Ast): Option[(Ast, Ident, Ast)] =
      ast match {
        case Map(a: GroupBy, b, c) => None
        case Map(a, b, c) => Some((a, b, c))
        case _ => None
      }
  }

  def unapply(q: Query): Option[Query] =
    q match {

      case Map(a: GroupBy, b, c) if (b == c) => None
      case Map(a: Nested, b, c) if (b == c) => None
      case Nested(DetachableMap(a: Join, b, c)) => None

      //  map(i => (i.i, i.l)).distinct.map(x => (x._1, x._2)) =>
      //    map(i => (i.i, i.l)).distinct
      case Map(Distinct(DetachableMap(a, b, c)), d, e) if isomorphic(e, c, d) =>
        Some(Distinct(Map(a, b, c)))

      // a.map(b => c).map(d => e) =>
      //    a.map(b => e[d := c])
      case Map(Map(a, b, c), d, e) =>
        val er = BetaReduction(e, d -> c)
        Some(Map(a, b, er))

      // a.map(b => b) =>
      //    a
      case Map(a: Query, b, c) if (b == c) =>
        Some(a)

      // a.map(b => c).flatMap(d => e) =>
      //    a.flatMap(b => e[d := c])
      case FlatMap(DetachableMap(a, b, c), d, e) =>
        val er = BetaReduction(e, d -> c)
        Some(FlatMap(a, b, er))

      // a.map(b => c).filter(d => e) =>
      //    a.filter(b => e[d := c]).map(b => c)
      case Filter(DetachableMap(a, b, c), d, e) =>
        val er = BetaReduction(e, d -> c)
        Some(Map(Filter(a, b, er), b, c))

      // a.map(b => c).sortBy(d => e) =>
      //    a.sortBy(b => e[d := c]).map(b => c)
      case SortBy(DetachableMap(a, b, c), d, e, f) =>
        val er = BetaReduction(e, d -> c)
        Some(Map(SortBy(a, b, er, f), b, c))

      // a.map(b => c).groupBy(d => e) =>
      //    a.groupBy(b => e[d := c]).map(x => (x._1, x._2.map(b => c)))
      case GroupBy(DetachableMap(a, b, c), d, e) =>
        val er = BetaReduction(e, d -> c)
        val x = Ident("x")
        val x1 = Property(Ident("x"), "_1")
        val x2 = Property(Ident("x"), "_2")
        val body = Tuple(List(x1, Map(x2, b, c)))
        Some(Map(GroupBy(a, b, er), x, body))

      // a.map(b => c).drop(d) =>
      //    a.drop(d).map(b => c)
      case Drop(DetachableMap(a, b, c), d) =>
        Some(Map(Drop(a, d), b, c))

      // a.map(b => c).take(d) =>
      //    a.drop(d).map(b => c)
      case Take(DetachableMap(a, b, c), d) =>
        Some(Map(Take(a, d), b, c))

      // a.map(b => c).nested =>
      //    a.nested.map(b => c)
      case Nested(DetachableMap(a, b, c)) =>
        Some(Map(Nested(a), b, c))

      // a.map(b => c).*join(d.map(e => f)).on((iA, iB) => on)
      //    a.*join(d).on((b, e) => on[iA := c, iB := f]).map(t => (c[b := t._1], f[e := t._2]))
      case Join(tpe, DetachableMap(a, b, c), DetachableMap(d, e, f), iA, iB, on) =>
        val onr = BetaReduction(on, iA -> c, iB -> f)
        val t = Ident("t")
        val t1 = BetaReduction(c, b -> Property(t, "_1"))
        val t2 = BetaReduction(f, e -> Property(t, "_2"))
        Some(Map(Join(tpe, a, d, b, e, onr), t, Tuple(List(t1, t2))))

      // a.*join(b.map(c => d)).on((iA, iB) => on)
      //    a.*join(b).on((iA, c) => on[iB := d]).map(t => (t._1, d[c := t._2]))
      case Join(tpe, a, DetachableMap(b, c, d), iA, iB, on) =>
        val onr = BetaReduction(on, iB -> d)
        val t = Ident("t")
        val t1 = Property(t, "_1")
        val t2 = BetaReduction(d, c -> Property(t, "_2"))
        Some(Map(Join(tpe, a, b, iA, c, onr), t, Tuple(List(t1, t2))))

      // a.map(b => c).*join(d).on((iA, iB) => on)
      //    a.*join(d).on((b, iB) => on[iA := c]).map(t => (c[b := t._1], t._2))
      case Join(tpe, DetachableMap(a, b, c), d, iA, iB, on) =>
        val onr = BetaReduction(on, iA -> c)
        val t = Ident("t")
        val t1 = BetaReduction(c, b -> Property(t, "_1"))
        val t2 = Property(t, "_2")
        Some(Map(Join(tpe, a, d, b, iB, onr), t, Tuple(List(t1, t2))))

      case other => None
    }
}