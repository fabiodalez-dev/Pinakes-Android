# Standalone articles (Pinakes #412)

The Emeroteca section now offers **Articles** when `GET /api/v1/periodicals/health`
returns `data.capabilities.standalone_articles = true`. Older servers omit this
capability and keep the existing masthead → year → issue → contents workflow.

Articles can be searched by title, author, publication or keywords. A publication's
screen links to the same list filtered by `testata_id`. Pagination follows
`meta.next_cursor`, preserves the filter, avoids duplicate IDs and exposes retry
on a failed page. Search changes invalidate earlier requests immediately.

The article detail preserves partial dates (such as June 2019), volume/issue
strings and page spans (including roman numerals). The publication and issue
links appear only when the server supplies their IDs. No holding or lending
status is inferred from the existence of an article.

PDFs open in an external viewer only when `has_public_pdf` is true and `pdf_url`
is a valid HTTP(S) URL. The server resolves the URL, including a possible
installation subdirectory. Servers implementing the original capability without
`pdf_url` can still display article metadata; the PDF action stays hidden until
the server is updated. Refreshing an article that became private or was deleted
removes its previously displayed content and PDF action.

Covers use the server-resolved `cover_url` (including issue/masthead fallbacks).
Subtitles are displayed in both the list and the detail. Published resources are
shown only with `has_public_resource`: HTTP(S) addresses open in the browser;
archive/local references remain selectable text. Access conditions accompany the
resource. The website action preserves installation subdirectories and opens the
article page, where staff with a browser session can edit it or read private PDFs
through the existing administration permissions. The mobile bearer token is never
put in a browser URL.

## Wire contract

- `GET periodicals/articles?q=&testata_id=&cursor=` → core envelope with an array.
- `GET periodicals/articles/{id}` → core envelope with one article; private or
  deleted records return 404.
- Article fields use Italian names (`titolo`, `autori`, `contenitore_titolo`,
  `data_pubblicazione_testo`, `anno_pubblicazione`, `numero`, `pagine`, `testata_id`,
  `fascicolo_id`), mapped explicitly by Kotlin serialization. Existing issue
  index fields retain their English names.
- `pdf_url` is nullable. Internal filenames, shelf marks and private notes are
  never needed by this client.

This is a consultation feature. Cataloguing, CSV import/export, associations and
publication permissions remain in the web administration interface.

Translations are provided in English, Italian, French and German through the
existing JSON resource generator. No new dependency is required.
