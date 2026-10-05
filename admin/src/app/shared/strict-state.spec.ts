import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { PageKind, StrictState } from './strict-state';

@Component({
  imports: [StrictState],
  template: `
    <strict-state [kind]="kind">
      <p>Body</p>
    </strict-state>
  `,
})
class Host {
  kind: PageKind = 'content';
}

describe('Page states', () => {
  async function render(kind: PageKind): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.kind = kind;
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a stable loading placeholder', async () => {
    const view = await render('loading');
    expect(view.querySelector('[aria-busy="true"]')).not.toBeNull();
    expect(view.textContent).not.toContain('Body');
  });

  it('shows empty content', async () => {
    const view = await render('empty');
    expect(view.querySelector('[role="status"]')?.textContent).toContain('Body');
  });

  it('shows an error', async () => {
    const view = await render('error');
    expect(view.querySelector('[role="alert"]')?.textContent).toContain('Body');
  });

  it('shows content', async () => {
    const view = await render('content');
    expect(view.textContent).toContain('Body');
    expect(view.querySelector('[role="alert"]')).toBeNull();
  });
});
