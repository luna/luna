package org.enso.librarymanager.published.repository

object EmptyRepository extends DummyRepository {
  override def libraries: Seq[EmptyRepository.DummyLibrary] = Seq.empty
}
