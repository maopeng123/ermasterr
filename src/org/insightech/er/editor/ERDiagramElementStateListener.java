package org.insightech.er.editor;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.DocumentProviderRegistry;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.IElementStateListener;
import org.insightech.er.ERDiagramActivator;

public class ERDiagramElementStateListener implements IElementStateListener {

    private IDocumentProvider documentProvider;

    private final ERDiagramMultiPageEditor editorPart;

    public ERDiagramElementStateListener(final ERDiagramMultiPageEditor editorPart) {
        this.editorPart = editorPart;

        documentProvider = DocumentProviderRegistry.getDefault().getDocumentProvider(editorPart.getEditorInput());

        documentProvider.addElementStateListener(this);

        try {
            documentProvider.connect(editorPart.getEditorInput());
        } catch (final CoreException e) {
            ERDiagramActivator.showExceptionDialog(e);
        }
    }

    @Override
    public void elementDirtyStateChanged(final Object element, final boolean isDirty) {}

    @Override
    public void elementContentAboutToBeReplaced(final Object element) {}

    @Override
    public void elementContentReplaced(final Object element) {}

    @Override
    public void elementDeleted(final Object deletedElement) {
        if (deletedElement != null && deletedElement.equals(editorPart.getEditorInput())) {
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    close(false);
                }
            };
            execute(r, false);
        }
    }

    @Override
    public void elementMoved(final Object originalElement, final Object movedElement) {
        if (originalElement != null && originalElement.equals(editorPart.getEditorInput())) {
            final boolean doValidationAsync = Display.getCurrent() != null;
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    if (movedElement == null || movedElement instanceof IEditorInput) {

                        final String previousContent;
                        IDocumentUndoManager previousUndoManager = null;
                        IDocument changed = null;
                        final boolean wasDirty = editorPart.isDirty();
                        changed = documentProvider.getDocument(editorPart.getEditorInput());
                        if (changed != null) {
                            if (wasDirty)
                                previousContent = changed.get();
                            else
                                previousContent = null;

                            previousUndoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(changed);
                            if (previousUndoManager != null)
                                previousUndoManager.connect(this);
                        } else
                            previousContent = null;

                        editorPart.setInputWithNotify((IEditorInput) movedElement);

                        if (previousUndoManager != null) {
                            final IDocument newDocument = documentProvider.getDocument(movedElement);
                            if (newDocument != null) {
                                final IDocumentUndoManager newUndoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(newDocument);
                                if (newUndoManager != null)
                                    newUndoManager.transferUndoHistory(previousUndoManager);
                            }
                            previousUndoManager.disconnect(this);
                        }

                        if (wasDirty && changed != null) {
                            final Runnable r2 = new Runnable() {
                                @Override
                                public void run() {
                                    documentProvider.getDocument(editorPart.getEditorInput()).set(previousContent);

                                }
                            };
                            execute(r2, doValidationAsync);
                        }

                    }
                }
            };
            execute(r, false);
        }
    }

    private void execute(final Runnable runnable, final boolean postAsync) {
        if (postAsync || Display.getCurrent() == null) {
            editorPart.getSite().getShell().getDisplay().asyncExec(runnable);
        } else {
            runnable.run();
        }
    }

    public void close(final boolean save) {
        final Display display = editorPart.getSite().getShell().getDisplay();
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                editorPart.getSite().getPage().closeEditor(editorPart, save);
            }
        });
    }

    protected void disposeDocumentProvider() {
        if (documentProvider != null) {

            final IEditorInput input = editorPart.getEditorInput();
            if (input != null) {
                documentProvider.disconnect(input);
            }

            documentProvider.removeElementStateListener(this);
        }
        documentProvider = null;
    }

}
